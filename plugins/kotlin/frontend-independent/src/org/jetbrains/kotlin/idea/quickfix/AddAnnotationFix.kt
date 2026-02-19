// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.renderer.render

open class AddAnnotationFix(
    element: KtElement,
    private val annotationClassId: ClassId,
    private val kind: Kind = Kind.Self,
    private val annotationInnerText: String? = null,
) : PsiUpdateModCommandAction<KtElement>(element) {

    override fun getPresentation(context: ActionContext, element: KtElement): Presentation? {
        val annotationCall = annotationClassId.shortClassName.render() + renderArgumentsForIntentionName()
        val actionName = when (kind) {
            Kind.Self -> KotlinBundle.message("fix.add.annotation.text.self", annotationCall)
            Kind.Constructor -> KotlinBundle.message("fix.add.annotation.text.constructor", annotationCall)
            is Kind.Declaration -> KotlinBundle.message("fix.add.annotation.text.declaration", annotationCall, kind.name ?: "?")
            is Kind.ContainingClass -> KotlinBundle.message("fix.add.annotation.text.containing.class", annotationCall, kind.name ?: "?")
            is Kind.Copy -> KotlinBundle.message("fix.add.annotation.with.arguments.text.copy", annotationCall, kind.source, kind.target)
        }
        return Presentation.of(actionName)
    }

    protected open fun renderArgumentsForIntentionName(): String = annotationInnerText.orEmpty()

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.annotation.family")

    override fun invoke(context: ActionContext, element: KtElement, updater: ModPsiUpdater) {
        element.addAnnotation(annotationClassId, annotationInnerText, searchForExistingEntry = false)
    }

    sealed class Kind {
        data object Self : Kind()
        data object Constructor : Kind()
        data class Declaration(val name: String?) : Kind()
        data class ContainingClass(val name: String?) : Kind()
        data class Copy(val source: String, val target: String) : Kind()
    }
}
