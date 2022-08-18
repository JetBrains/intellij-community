// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.idea.base.projectStructure.allModules
import org.jetbrains.kotlin.idea.base.projectStructure.getWholeModuleGroup
import org.jetbrains.kotlin.idea.base.util.substringAfterLastOrNull
import org.jetbrains.kotlin.idea.base.util.substringBeforeLastOrNull
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
import org.jetbrains.kotlin.idea.inspections.ReplaceStringInDocumentFix
import org.jetbrains.kotlin.idea.versions.DEPRECATED_LIBRARIES_INFORMATION
import org.jetbrains.kotlin.idea.versions.DeprecatedLibInfo
import org.jetbrains.kotlin.idea.versions.LibInfo
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression

private val LibInfo.gradleMarker get() = "$groupId:$name"

class DeprecatedGradleDependencyInspection : BaseInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(): BaseInspectionVisitor = DependencyFinder()

    private open class DependencyFinder : KotlinGradleInspectionVisitor() {
        override fun visitClosure(closure: GrClosableBlock) {
            super.visitClosure(closure)

            val dependenciesCall = closure.getStrictParentOfType<GrMethodCall>() ?: return
            if (dependenciesCall.invokedExpression.text != "dependencies") return

            val dependencyEntries = GradleHeuristicHelper.findStatementWithPrefixes(
                closure, SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
            )
            for (dependencyStatement in dependencyEntries) {
                visitDependencyEntry(dependencyStatement)
            }
        }

        open fun visitDependencyEntry(dependencyStatement: GrCallExpression) {
            for (outdatedInfo in DEPRECATED_LIBRARIES_INFORMATION) {
                val dependencyText = dependencyStatement.text
                val libMarker = outdatedInfo.old.gradleMarker

                if (dependencyText.contains(libMarker)) {
                    val afterMarkerChar = dependencyText.substringAfter(libMarker).getOrNull(0)
                    if (!(afterMarkerChar == '\'' || afterMarkerChar == '"' || afterMarkerChar == ':')) {
                        continue
                    }

                    val libVersion =
                        DifferentStdlibGradleVersionInspection.getRawResolvedLibVersion(
                            dependencyStatement.containingFile, outdatedInfo.old.groupId, listOf(outdatedInfo.old.name)
                        ) ?: libraryVersionFromOrderEntry(dependencyStatement.containingFile, outdatedInfo.old.name)


                    if (libVersion != null && VersionComparatorUtil.COMPARATOR.compare(
                            libVersion,
                            outdatedInfo.outdatedAfterVersion
                        ) >= 0
                    ) {
                        val reportOnElement = reportOnElement(dependencyStatement, outdatedInfo)

                        registerError(
                            reportOnElement, outdatedInfo.message,
                            arrayOf(ReplaceStringInDocumentFix(reportOnElement, outdatedInfo.old.name, outdatedInfo.new.name)),
                            ProblemHighlightType.LIKE_DEPRECATED
                        )

                        break
                    }
                }
            }
        }

        private fun reportOnElement(classpathEntry: GrCallExpression, deprecatedInfo: DeprecatedLibInfo): PsiElement {
            val indexOf = classpathEntry.text.indexOf(deprecatedInfo.old.name)
            if (indexOf < 0) return classpathEntry

            return classpathEntry.findElementAt(indexOf) ?: classpathEntry
        }

    }

    private class ExternalLibraryInfo(val artifactId: String, val version: String)

    companion object {
        fun libraryVersionFromOrderEntry(file: PsiFile, libraryId: String): String? {
            val module = ProjectRootManager.getInstance(file.project).fileIndex.getModuleForFile(file.virtualFile) ?: return null
            val libMarker = ":$libraryId:"

            for (moduleInGroup in module.getWholeModuleGroup().allModules()) {
                var libVersion: String? = null
                ModuleRootManager.getInstance(moduleInGroup).orderEntries().forEachLibrary { library ->
                    if (library.name?.contains(libMarker) == true) {
                        libVersion = parseExternalLibraryName(library)?.version
                    }

                    // Continue if nothing is found
                    libVersion == null
                }

                if (libVersion != null) {
                    return libVersion
                }
            }

            return null
        }

        private fun parseExternalLibraryName(library: Library): ExternalLibraryInfo? {
            val libName = library.name ?: return null

            val versionWithKind = libName.substringAfterLastOrNull(":") ?: return null
            val version = versionWithKind.substringBefore("@")

            val artifactId = libName.substringBeforeLastOrNull(":")?.substringAfterLastOrNull(":") ?: return null

            if (version.isBlank() || artifactId.isBlank()) return null

            return ExternalLibraryInfo(artifactId, version)
        }
    }
}