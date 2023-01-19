// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.KotlinType

class ChangeTypeFix(element: KtTypeReference, private val type: KotlinType) : KotlinQuickFixAction<KtTypeReference>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.change.type.family")

    override fun getText(): String {
        val currentTypeText = element?.text ?: return ""
        return KotlinBundle.message(
            "fix.change.type.text",
            currentTypeText, renderTypeWithFqNameOnClash(type, currentTypeText)
        )
    }

    private fun renderTypeWithFqNameOnClash(type: KotlinType, nameToCheckAgainst: String?): String {
        val fqNameToCheckAgainst = FqName(nameToCheckAgainst!!)
        val typeClassifierDescriptor = type.constructor.declarationDescriptor
        val typeFqName = typeClassifierDescriptor?.let(DescriptorUtils::getFqNameSafe) ?: fqNameToCheckAgainst
        val renderer = when {
            typeFqName.shortName() == fqNameToCheckAgainst.shortName() -> IdeDescriptorRenderers.SOURCE_CODE
            else -> IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS
        }
        return renderer.renderType(type)
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val newTypeRef = element.replaced(KtPsiFactory(project).createType(IdeDescriptorRenderers.SOURCE_CODE.renderType(type)))
        ShortenReferences.DEFAULT.process(newTypeRef)
    }

    companion object : KotlinSingleIntentionActionFactoryWithDelegate<KtTypeReference, KotlinType>() {
        override fun getElementOfInterest(diagnostic: Diagnostic) =
            Errors.EXPECTED_PARAMETER_TYPE_MISMATCH.cast(diagnostic).psiElement.typeReference

        override fun extractFixData(element: KtTypeReference, diagnostic: Diagnostic) =
            Errors.EXPECTED_PARAMETER_TYPE_MISMATCH.cast(diagnostic).a

        override fun createFix(originalElement: KtTypeReference, data: KotlinType) =
            ChangeTypeFix(originalElement, data)
    }
}
