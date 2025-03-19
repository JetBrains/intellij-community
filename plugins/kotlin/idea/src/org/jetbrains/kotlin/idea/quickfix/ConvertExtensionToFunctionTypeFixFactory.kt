// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

object ConvertExtensionToFunctionTypeFixFactory : KotlinIntentionActionsFactory() {
    private fun KotlinType.renderType(renderer: DescriptorRenderer) = buildString {
        append('(')
        arguments.dropLast(1).joinTo(this@buildString, ", ") { renderer.renderType(it.type) }
        append(") -> ")
        append(renderer.renderType(this@renderType.getReturnTypeFromFunctionType()))
    }

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {

        val casted = Errors.SUPERTYPE_IS_EXTENSION_FUNCTION_TYPE.cast(diagnostic)
        val element = casted.psiElement

        val type = element.analyze(BodyResolveMode.PARTIAL).get(BindingContext.TYPE, element) ?: return emptyList()
        if (!type.isExtensionFunctionType) return emptyList()
        val targetTypeStringShort = type.renderType(IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS)
        val targetTypeStringLong = type.renderType(IdeDescriptorRenderers.SOURCE_CODE)

        return listOf(ConvertExtensionToFunctionTypeFix(element, targetTypeStringShort, targetTypeStringLong).asIntention())
    }
}
