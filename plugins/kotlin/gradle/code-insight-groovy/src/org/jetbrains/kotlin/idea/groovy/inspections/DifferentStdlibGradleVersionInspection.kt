// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.findGradleProjectStructure
import org.jetbrains.kotlin.idea.groovy.KotlinGroovyBundle
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression

class DifferentStdlibGradleVersionInspection : BaseInspection() {
    override fun buildVisitor(): BaseInspectionVisitor = MyVisitor(KOTLIN_GROUP_ID, JvmIdePlatformKind.tooling.mavenLibraryIds)

    override fun buildErrorString(vararg args: Any) =
        KotlinGroovyBundle.message("error.text.different.kotlin.library.version", args[0], args[1])

    private abstract class VersionFinder(private val groupId: String, private val libraryIds: List<String>) :
        KotlinGradleInspectionVisitor() {
        protected abstract fun onFound(stdlibVersion: IdeKotlinVersion, stdlibStatement: GrCallExpression)

        override fun visitClosure(closure: GrClosableBlock) {
            super.visitClosure(closure)

            val dependenciesCall = closure.getStrictParentOfType<GrMethodCall>() ?: return
            if (dependenciesCall.invokedExpression.text != "dependencies") return

            if (dependenciesCall.parent !is PsiFile) return

            val stdlibStatement = findLibraryStatement(closure, "org.jetbrains.kotlin", libraryIds) ?: return
            val stdlibVersion = getResolvedLibVersion(closure.containingFile, groupId, libraryIds) ?: return

            onFound(stdlibVersion, stdlibStatement)
        }
    }

    private inner class MyVisitor(groupId: String, libraryIds: List<String>) : VersionFinder(groupId, libraryIds) {
        override fun onFound(stdlibVersion: IdeKotlinVersion, stdlibStatement: GrCallExpression) {
            val gradlePluginVersion = findResolvedKotlinGradleVersion(stdlibStatement.containingFile)

            if (stdlibVersion != gradlePluginVersion) {
                registerError(stdlibStatement, gradlePluginVersion, stdlibVersion)
            }
        }
    }

    companion object {
        private fun findLibraryStatement(
            closure: GrClosableBlock,
            @NonNls libraryGroup: String,
            libraryIds: List<String>
        ): GrCallExpression? {
            return GradleHeuristicHelper.findStatementWithPrefixes(closure, SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS).firstOrNull { statement ->
                libraryIds.any {
                    val index = statement.text.indexOf(it)
                    // This prevents detecting kotlin-stdlib inside kotlin-stdlib-common, -jdk8, etc.
                    index != -1 && statement.text.getOrNull(index + it.length) != '-'
                } && statement.text.contains(libraryGroup)
            }
        }

        fun getRawResolvedLibVersion(file: PsiFile, groupId: String, libraryIds: List<String>): String? {
            val projectStructureNode = findGradleProjectStructure(file) ?: return null
            val module = ProjectRootManager.getInstance(file.project).fileIndex.getModuleForFile(file.virtualFile) ?: return null
            val gradleFacade = KotlinGradleFacade.getInstance() ?: return null

            for (moduleData in projectStructureNode.findAll(ProjectKeys.MODULE).filter { it.data.internalName == module.name }) {
                gradleFacade.findLibraryVersionByModuleData(moduleData.node, groupId, libraryIds)?.let {
                    return it
                }
            }

            return null
        }

        fun getResolvedLibVersion(file: PsiFile, groupId: String, libraryIds: List<String>): IdeKotlinVersion? {
            val rawVersion = getRawResolvedLibVersion(file, groupId, libraryIds) ?: return null
            return IdeKotlinVersion.opt(rawVersion)
        }
    }
}