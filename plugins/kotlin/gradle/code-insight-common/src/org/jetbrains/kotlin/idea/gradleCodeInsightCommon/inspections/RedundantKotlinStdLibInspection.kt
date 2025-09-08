// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.startLine
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleProjectData
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.findGradleProjectStructure
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

private val LOG = logger<RedundantKotlinStdLibInspection>()

class RedundantKotlinStdLibInspection : LocalInspectionTool() {
    override fun isAvailableForFile(file: PsiFile): Boolean {
        val kotlinStdlibDependencyByDefaultProp = readGradleProperty(file.project, "kotlin.stdlib.default.dependency")
        // if the property is not set (null value), then the inspection should be active
        // and only if it is "false" should it be deactivated as that is how it works in Gradle
        if (kotlinStdlibDependencyByDefaultProp == "false") return false

        val language = file.language
        val inspectionProvider = GradleKotlinInspectionProvider.INSTANCE.forLanguage(language) ?: return false
        return inspectionProvider.isRedundantKotlinStdLibInspectionAvailable(file)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val language = holder.file.language
        val inspectionProvider = GradleKotlinInspectionProvider.INSTANCE.forLanguage(language) ?: return PsiElementVisitor.EMPTY_VISITOR
        return inspectionProvider.getRedundantKotlinStdLibInspectionVisitor(holder, isOnTheFly)
    }
}

fun isKotlinStdLibDependency(resolvedCatalogReference: PsiMethod, reference: PsiElement): Boolean {
    if (!isInVersionCatalogAccessor(resolvedCatalogReference)) return false
    val origin = findOriginInTomlFile(resolvedCatalogReference, reference) as? TomlKeyValue ?: return false
    val (dependencyGroup, dependencyName) = when (val originValue = origin.value) {
        is TomlLiteral -> originValue.text.cleanRawString().split(":").takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return false
        is TomlInlineTable -> {
            val module = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "module" }
            if (module != null) {
                val moduleValue = module.value
                if (moduleValue !is TomlLiteral) return false
                moduleValue.text.cleanRawString().split(":").takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return false
            } else {
                val group = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "group" }
                val name = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "name" }
                if (group == null || name == null) return false
                val groupValue = group.value
                val nameValue = name.value
                if (groupValue !is TomlLiteral || nameValue !is TomlLiteral) return false
                groupValue.text.cleanRawString() to nameValue.text.cleanRawString()
            }
        }

        else -> return false
    }

    if (LOG.isDebugEnabled) {
        val gradleFile = reference.containingFile
        LOG.debug(
            "Found a version catalog dependency: $dependencyGroup:$dependencyName " +
                    "at line ${reference.startLine(gradleFile.fileDocument) + 1} " +
                    "in file ${gradleFile.virtualFile.path} " +
                    "from version catalog ${origin.containingFile.virtualFile.path}"
        )
    }

    return dependencyGroup == KOTLIN_GROUP_ID && dependencyName == KOTLIN_JAVA_STDLIB_NAME
}

fun findResolvedKotlinJvmVersion(file: PsiFile): IdeKotlinVersion? {
    val module = ModuleUtilCore.findModuleForFile(file.virtualFile, file.project) ?: return null
    val projectStructureNode = findGradleProjectStructure(module) ?: return null

    for (node in ExternalSystemApiUtil.findAll(projectStructureNode, ProjectKeys.MODULE)) {
        if (node.data.internalName == module.name) {
            val kotlinGradleProjectData = node.findAll(KotlinGradleProjectData.KEY).firstOrNull()?.data ?: return null
            if (kotlinGradleProjectData.platformPluginId != "kotlin-platform-jvm") return null
            val rawVersion = kotlinGradleProjectData.kotlinGradlePluginVersion?.versionString ?: return null
            return IdeKotlinVersion.opt(rawVersion)
        }
    }

    return null
}

fun getResolvedLibVersion(file: PsiFile, groupId: String, libraryIds: List<String>): IdeKotlinVersion? {
    val projectStructureNode = findGradleProjectStructure(file) ?: return null
    val module = ProjectRootManager.getInstance(file.project).fileIndex.getModuleForFile(file.virtualFile) ?: return null
    val gradleFacade = KotlinGradleFacade.getInstance() ?: return null

    for (moduleData in projectStructureNode.findAll(ProjectKeys.MODULE).filter { it.data.internalName == module.name }) {
        gradleFacade.findLibraryVersionByModuleData(moduleData.node, groupId, libraryIds)?.let {
            return IdeKotlinVersion.opt(it)
        }
    }

    return null
}

private fun String.cleanRawString(): String {
    return this.removeSurrounding("\"\"\"")
        .removeSurrounding("'''")
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .replace("\r", "")
        .replace("\n", "")
}
