// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.getInstance

interface KotlinSerializationEnabledChecker {
    fun isEnabledFor(moduleDescriptor: ModuleDescriptor): Boolean

    class Default : KotlinSerializationEnabledChecker {
        @OptIn(K1ModeProjectStructureApi::class)
        override fun isEnabledFor(moduleDescriptor: ModuleDescriptor): Boolean {
            val module = moduleDescriptor.getCapability(ModuleInfo.Capability)?.unwrapModuleSourceInfo()?.module ?: return false
            val pluginClasspath = KotlinCommonCompilerArgumentsHolder.getInstance(module).pluginClasspaths ?: return false
            return pluginClasspath.any(KotlinSerializationImportHandler::isPluginJarPath)
        }
    }

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinSerializationEnabledChecker>("org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.kotlinSerializationEnabledChecker")

        fun isEnabledIn(moduleDescriptor: ModuleDescriptor): Boolean {
            return EP_NAME.extensions.any { it.isEnabledFor(moduleDescriptor) }
        }
    }
}
