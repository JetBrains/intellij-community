// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.getInstance
import org.jetbrains.kotlin.resolve.descriptorUtil.module

private fun isEnabledIn(moduleDescriptor: ModuleDescriptor): Boolean {
    val module = moduleDescriptor.getCapability(ModuleInfo.Capability)?.unwrapModuleSourceInfo()?.module ?: return false
    val pluginClasspath = KotlinCommonCompilerArgumentsHolder.getInstance(module).pluginClasspaths ?: return false
    return pluginClasspath.any(KotlinSerializationImportHandler::isPluginJarPath)
}

fun <T> getIfEnabledOn(clazz: ClassDescriptor, body: () -> T): T? {
    return if (isEnabledIn(clazz.module)) body() else null
}

fun runIfEnabledOn(clazz: ClassDescriptor, body: () -> Unit) { getIfEnabledOn<Unit>(clazz, body) }

fun runIfEnabledIn(moduleDescriptor: ModuleDescriptor, block: () -> Unit) { if (isEnabledIn(moduleDescriptor)) block() }