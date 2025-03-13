// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.LATEINIT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_KEYWORD
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils

internal object AddModifierFixMppFactory : AddModifierFix.Factory<ModCommandAction> {
    override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): ModCommandAction =
        AddModifierFixMpp(element, modifier)
}

internal object MakeClassOpenFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val typeReference = diagnostic.psiElement as KtTypeReference
        val declaration = typeReference.classForRefactor() ?: return null
        if (declaration.isAnnotation() || declaration.isEnum() || declaration.isData() || declaration.isInlineOrValue()) return null
        return AddModifierFixMpp(declaration, OPEN_KEYWORD).asIntention()
    }
}

internal object AddLateinitFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val property = Errors.MUST_BE_INITIALIZED_OR_BE_ABSTRACT.cast(diagnostic).psiElement
        if (!property.isVar) return null

        val descriptor = property.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return null
        val type = (descriptor as? PropertyDescriptor)?.type ?: return null

        if (TypeUtils.isNullableType(type)) return null
        if (KotlinBuiltIns.isPrimitiveType(type)) return null

        return AddModifierFix(property, LATEINIT_KEYWORD).asIntention()
    }
}

fun KtTypeReference.classForRefactor(): KtClass? {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext[BindingContext.TYPE, this] ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    val declaration = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass ?: return null
    return declaration.takeIf { declaration.canRefactorElement() }
}