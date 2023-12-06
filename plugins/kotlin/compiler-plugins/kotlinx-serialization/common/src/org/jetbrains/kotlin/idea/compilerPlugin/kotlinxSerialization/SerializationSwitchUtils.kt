// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.module

private fun isEnabledIn(moduleDescriptor: ModuleDescriptor): Boolean {
    return KotlinSerializationEnabledChecker.isEnabledIn(moduleDescriptor)
}

fun <T> getIfEnabledOn(clazz: ClassDescriptor, body: () -> T): T? {
    return if (isEnabledIn(clazz.module)) body() else null
}

fun runIfEnabledOn(clazz: ClassDescriptor, body: () -> Unit) { getIfEnabledOn(clazz, body) }

fun runIfEnabledIn(moduleDescriptor: ModuleDescriptor, block: () -> Unit) { if (isEnabledIn(moduleDescriptor)) block() }