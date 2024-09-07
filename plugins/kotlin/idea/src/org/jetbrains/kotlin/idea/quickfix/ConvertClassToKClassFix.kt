// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal val JAVA_LANG_CLASS_FQ_NAME = FqName("java.lang.Class")

internal fun KotlinType.isJClass(): Boolean {
    val expressionTypeFqName = constructor.declarationDescriptor?.fqNameSafe ?: return false
    return expressionTypeFqName == JAVA_LANG_CLASS_FQ_NAME
}

internal object ConvertClassToKClassFixFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val casted = Errors.TYPE_MISMATCH.cast(diagnostic)
        val element = casted.psiElement as? KtDotQualifiedExpression ?: return emptyList()

        val expectedClassDescriptor = casted.a.constructor.declarationDescriptor as? ClassDescriptor ?: return emptyList()
        if (!KotlinBuiltIns.isKClass(expectedClassDescriptor)) return emptyList()

        val bindingContext = element.analyze(BodyResolveMode.PARTIAL)
        val expressionType = bindingContext.getType(element) ?: return emptyList()
        if (!expressionType.isJClass()) return emptyList()

        val children = element.children
        if (children.size != 2) return emptyList()

        val firstChild = children.first() as? KtExpression ?: return emptyList()
        val firstChildType = bindingContext.getType(firstChild) ?: return emptyList()

        if (!firstChildType.isSubtypeOf(casted.a)) return emptyList()

        return listOf(ConvertClassToKClassFix(element).asIntention())
    }
}