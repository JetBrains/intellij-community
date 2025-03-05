// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.extensions

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.runIfEnabledIn
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationLoweringExtension

class SerializationIDEIrExtension : SerializationLoweringExtension() {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        @OptIn(ObsoleteDescriptorBasedAPI::class)
        runIfEnabledIn(pluginContext.moduleDescriptor) {
            super.generate(moduleFragment, pluginContext)
        }
    }
}
