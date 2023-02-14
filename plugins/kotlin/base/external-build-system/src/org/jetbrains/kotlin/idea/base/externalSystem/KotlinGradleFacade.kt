// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.externalSystem

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import javax.swing.Icon

interface KotlinGradleFacade {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinGradleFacade? = serviceOrNull()
    }

    val gradleIcon: Icon

    val runConfigurationFactory: ConfigurationFactory

    fun isDelegatedBuildEnabled(module: Module): Boolean

    fun findKotlinPluginVersion(node: DataNode<ModuleData>): IdeKotlinVersion?

    fun findLibraryVersionByModuleData(node: DataNode<*>, groupId: String, libraryIds: List<String>): String?
}