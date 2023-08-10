// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.extensions

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.declaration.DeclarationBodyVisitor
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.runIfEnabledIn
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.runIfEnabledOn
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationCodegenExtension
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationJsExtension
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationLoweringExtension

class SerializationIDECodegenExtension : SerializationCodegenExtension() {
    override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) =
        runIfEnabledOn(codegen.descriptor) { super.generateClassSyntheticParts(codegen) }
}

class SerializationIDEJsExtension : SerializationJsExtension() {
    override fun generateClassSyntheticParts(
        declaration: KtPureClassOrObject,
        descriptor: ClassDescriptor,
        translator: DeclarationBodyVisitor,
        context: TranslationContext
    ) = runIfEnabledOn(descriptor) {
        super.generateClassSyntheticParts(declaration, descriptor, translator, context)
    }
}

class SerializationIDEIrExtension : SerializationLoweringExtension() {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        runIfEnabledIn(pluginContext.moduleDescriptor) {
            super.generate(moduleFragment, pluginContext)
        }
    }
}