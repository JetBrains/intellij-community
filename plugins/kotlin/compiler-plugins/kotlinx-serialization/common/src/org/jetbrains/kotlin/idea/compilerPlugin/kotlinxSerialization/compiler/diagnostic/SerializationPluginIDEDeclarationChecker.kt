// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.diagnostic

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.getIfEnabledOn
import org.jetbrains.kotlinx.serialization.compiler.diagnostic.SerializationPluginDeclarationChecker

class SerializationPluginIDEDeclarationChecker : SerializationPluginDeclarationChecker() {
    override fun serializationPluginEnabledOn(descriptor: ClassDescriptor): Boolean {
        // In the IDE, plugin is always in the classpath, but enabled only if corresponding compiler settings
        // were imported into project model from Gradle.
        return getIfEnabledOn(descriptor) { true } == true
    }

    override val isIde: Boolean
        get() = true
}