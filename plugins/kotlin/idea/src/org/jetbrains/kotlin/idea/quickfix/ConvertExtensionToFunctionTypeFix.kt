// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

class ConvertExtensionToFunctionTypeFix(element: KtTypeReference, type: KotlinType) : KotlinQuickFixAction<KtTypeReference>(element) {

    private val targetTypeStringShort = type.renderType(IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS)
    private val targetTypeStringLong = type.renderType(IdeDescriptorRenderers.SOURCE_CODE)

    override fun getText() = KotlinBundle.message("convert.supertype.to.0", targetTypeStringShort)
    override fun getFamilyName() = KotlinBundle.message("convert.extension.function.type.to.regular.function.type")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val replaced = element.replaced(KtPsiFactory(project).createType(targetTypeStringLong))
        ShortenReferences.DEFAULT.process(replaced)
    }

    private fun KotlinType.renderType(renderer: DescriptorRenderer) = buildString {
        append('(')
        arguments.dropLast(1).joinTo(this@buildString, ", ") { renderer.renderType(it.type) }
        append(") -> ")
        append(renderer.renderType(this@renderType.getReturnTypeFromFunctionType()))
    }

    companion object Factory : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val casted = Errors.SUPERTYPE_IS_EXTENSION_FUNCTION_TYPE.cast(diagnostic)
            val element = casted.psiElement

            val type = element.analyze(BodyResolveMode.PARTIAL).get(BindingContext.TYPE, element) ?: return emptyList()
            if (!type.isExtensionFunctionType) return emptyList()

            return listOf(ConvertExtensionToFunctionTypeFix(element, type))
        }
    }
}
