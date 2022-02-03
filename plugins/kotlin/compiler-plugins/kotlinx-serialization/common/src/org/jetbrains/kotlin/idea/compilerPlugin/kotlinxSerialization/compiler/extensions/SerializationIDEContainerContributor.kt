// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.extensions

import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.diagnostic.SerializationPluginIDEDeclarationChecker
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.diagnostic.JsonFormatRedundantDiagnostic

class SerializationIDEContainerContributor : StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        container.useInstance(SerializationPluginIDEDeclarationChecker())
        container.useInstance(JsonFormatRedundantDiagnostic())
    }
}
