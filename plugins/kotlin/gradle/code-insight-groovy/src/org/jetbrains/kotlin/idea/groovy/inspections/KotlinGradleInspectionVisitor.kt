// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.extensions.gradle.KotlinGradleConstants
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.findGradleProjectStructure
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

const val KOTLIN_PLUGIN_CLASSPATH_MARKER = "${KotlinGradleConstants.GROUP_ID}:${KotlinGradleConstants.GRADLE_PLUGIN_ID}:"

abstract class KotlinGradleInspectionVisitor : BaseInspectionVisitor() {
    override fun visitFile(file: GroovyFileBase) {
        if (!FileUtilRt.extensionEquals(file.name, KotlinGradleConstants.GROOVY_EXTENSION)) return

        val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex

        if (!isUnitTestMode()) {
            val module = fileIndex.getModuleForFile(file.virtualFile) ?: return
            if (!module.isGradleModule) return
        }

        if (fileIndex.isExcluded(file.virtualFile)) return

        super.visitFile(file)
    }
}

fun findResolvedKotlinGradleVersion(file: PsiFile): IdeKotlinVersion? =
    ModuleUtilCore.findModuleForFile(file.virtualFile, file.project)?.let { findResolvedKotlinGradleVersion(it) }

fun findResolvedKotlinGradleVersion(module: Module): IdeKotlinVersion? {
    val projectStructureNode = findGradleProjectStructure(module) ?: return null
    val gradleFacade = KotlinGradleFacade.getInstance() ?: return null

    for (node in ExternalSystemApiUtil.findAll(projectStructureNode, ProjectKeys.MODULE)) {
        if (node.data.internalName == module.name) {
            val kotlinPluginVersion = gradleFacade.findKotlinPluginVersion(node)
            if (kotlinPluginVersion != null) {
                return kotlinPluginVersion
            }
        }
    }

    return null
}
