// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.cfg.LeakingThisDescriptor.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
import org.jetbrains.kotlin.idea.util.safeAnalyzeWithContentNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext.LEAKING_THIS
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

class LeakingThisInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = classVisitor { klass ->
        // We still use analyzeWithAllCompilerChecks() here.
        // It's possible to use analyze(), but then we should repeat class constructor consistency check
        // for different class internal elements, like KtProperty and KtClassInitializer.
        // It can affect performance, so yet we want to avoid this.
        val context = klass.safeAnalyzeWithContentNonSourceRootCode()
        klass.forEachDescendantOfType(fun(expression: KtExpression) {
            val leakingThisDescriptor = context[LEAKING_THIS, expression] ?: return
            if (leakingThisDescriptor.classOrObject != klass) return
            val description: String = when (leakingThisDescriptor) {
                is NonFinalClass ->
                    if (expression is KtThisExpression && expression.getStrictParentOfType<KtClassLiteralExpression>() == null) {
                        val name = leakingThisDescriptor.klass.name
                        klass.createDescription(
                            KotlinBundle.message("leaking.this.in.constructor.of.non.final.class.0", name),
                            KotlinBundle.message("leaking.this.in.constructor.of.enum.class.0.with.overridable.members", name)
                        ) { it.hasOverriddenMember() } ?: return
                    } else {
                        return // Not supported yet
                    }

                is NonFinalProperty -> {
                    val name = leakingThisDescriptor.property.name.asString()
                    klass.createDescription(KotlinBundle.message("accessing.non.final.property.0.in.constructor", name)) {
                        it.hasOverriddenMember { owner -> owner.name == name }
                    } ?: return
                }

                is NonFinalFunction -> {
                    val function = leakingThisDescriptor.function
                    klass.createDescription(KotlinBundle.message("calling.non.final.function.0.in.constructor", function.name)) {
                        it.hasOverriddenMember { owner ->
                            owner is KtNamedFunction &&
                                    owner.name == function.name.asString() &&
                                    owner.valueParameters.size == function.valueParameters.size
                        }
                    } ?: return
                }

                else -> return // Not supported yet
            }

            val memberDescriptorToFix = when (leakingThisDescriptor) {
                is NonFinalProperty -> leakingThisDescriptor.property
                is NonFinalFunction -> leakingThisDescriptor.function
                else -> null
            }

            val memberFix = memberDescriptorToFix?.let {
                if (it.modality == Modality.OPEN) {
                    val modifierListOwner = DescriptorToSourceUtils.descriptorToDeclaration(it) as? KtDeclaration
                    createMakeFinalFix(modifierListOwner)
                } else null
            }

            val classFix = if (klass.hasModifier(KtTokens.OPEN_KEYWORD)) createMakeFinalFix(klass) else null
            holder.registerProblem(
                expression, description,
                when (leakingThisDescriptor) {
                    is NonFinalProperty, is NonFinalFunction -> GENERIC_ERROR_OR_WARNING
                    else -> WEAK_WARNING
                },
                *(arrayOf(memberFix, classFix).filterNotNull().toTypedArray())
            )
        })
    }

    companion object {
        private fun createMakeFinalFix(declaration: KtDeclaration?): IntentionWrapper? {
            declaration ?: return null
            val useScope = declaration.useScope
            if (DefinitionsScopedSearch.search(declaration, useScope).findFirst() != null) return null
            if ((declaration.containingClassOrObject as? KtClass)?.isInterface() == true) return null
            return IntentionWrapper(AddModifierFixFE10(declaration, KtTokens.FINAL_KEYWORD))
        }
    }
}

@Nls
private fun KtClass.createDescription(
    @Nls defaultText: String,
    @Nls enumText: String = defaultText,
    check: (KtClass) -> Boolean
): String? = if (isEnum()) enumText.takeUnless { check(this) } else defaultText

private fun KtEnumEntry.hasOverriddenMember(additionalCheck: (KtDeclaration) -> Boolean): Boolean = declarations.any {
    it.hasModifier(KtTokens.OVERRIDE_KEYWORD) && additionalCheck(it)
}

private fun KtClass.hasOverriddenMember(filter: (KtDeclaration) -> Boolean = { true }): Boolean {
    val enumEntries = body?.getChildrenOfType<KtEnumEntry>() ?: return false
    return enumEntries.none {
        it.hasOverriddenMember(filter)
    }
}