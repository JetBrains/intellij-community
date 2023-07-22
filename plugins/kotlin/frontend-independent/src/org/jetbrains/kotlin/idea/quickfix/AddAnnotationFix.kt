// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

open class AddAnnotationFix(
    element: KtElement,
    private val annotationClassId: ClassId,
    private val kind: Kind = Kind.Self,
    private val argumentClassFqName: FqName? = null,
    private val existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : KotlinQuickFixAction<KtElement>(element) {
    override fun getText(): String {
        val annotationArguments = (argumentClassFqName?.shortName()?.let { "($it::class)" } ?: "")
        val annotationCall = annotationClassId.shortClassName.asString() + annotationArguments
        return when (kind) {
            Kind.Self -> KotlinBundle.message("fix.add.annotation.text.self", annotationCall)
            Kind.Constructor -> KotlinBundle.message("fix.add.annotation.text.constructor", annotationCall)
            is Kind.Declaration -> KotlinBundle.message("fix.add.annotation.text.declaration", annotationCall, kind.name ?: "?")
            is Kind.ContainingClass -> KotlinBundle.message("fix.add.annotation.text.containing.class", annotationCall, kind.name ?: "?")
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.annotation.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val annotationEntry = existingAnnotationEntry?.element
        val annotationInnerText = argumentClassFqName?.let { "${it.render()}::class" }
        if (annotationEntry != null) {
            if (annotationInnerText == null) return
            val psiFactory = KtPsiFactory(project)
            annotationEntry.valueArgumentList?.addArgument(psiFactory.createArgument(annotationInnerText))
                ?: annotationEntry.addAfter(psiFactory.createCallArguments("($annotationInnerText)"), annotationEntry.lastChild)
            ShortenReferencesFacility.getInstance().shorten(annotationEntry)
        } else {
            element.addAnnotation(annotationClassId, annotationInnerText, searchForExistingEntry = false)
        }
    }

    sealed class Kind {
        object Self : Kind()
        object Constructor : Kind()
        class Declaration(val name: String?) : Kind()
        class ContainingClass(val name: String?) : Kind()
    }

    object TypeVarianceConflictFactory :
        QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val typeReference = psiElement.parent as? KtTypeReference ?: return emptyList()
            return listOf(AddAnnotationFix(typeReference, ClassId.topLevel(StandardNames.FqNames.unsafeVariance), Kind.Self))
        }
    }

}
