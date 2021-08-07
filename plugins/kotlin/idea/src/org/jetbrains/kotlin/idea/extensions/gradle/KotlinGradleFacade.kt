// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions.gradle

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

interface KotlinGradleFacade {
    companion object {
        val instance: KotlinGradleFacade?
            get() = serviceOrNull()
    }

    val gradleIcon: Icon

    val runConfigurationFactory: ConfigurationFactory

    fun isDelegatedBuildEnabled(module: Module): Boolean

    fun findKotlinPluginVersion(node: DataNode<ModuleData>): String?

    fun findLibraryVersionByModuleData(node: DataNode<*>, groupId: String, libraryIds: List<String>): String?

    fun findManipulator(file: PsiFile, preferNewSyntax: Boolean = true): GradleBuildScriptManipulator<*>?
}

object KotlinGradleConstants {
    @NonNls const val GROUP_ID: String = "org.jetbrains.kotlin"
    @NonNls const val GRADLE_PLUGIN_ID: String = "kotlin-gradle-plugin"

    @NonNls const val GROOVY_EXTENSION = "gradle"
}

fun KotlinGradleFacade.getManipulator(file: PsiFile, preferNewSyntax: Boolean = true): GradleBuildScriptManipulator<*> {
    return findManipulator(file, preferNewSyntax) ?: error("Unknown build script file type (${file::class.qualifiedName})!")
}