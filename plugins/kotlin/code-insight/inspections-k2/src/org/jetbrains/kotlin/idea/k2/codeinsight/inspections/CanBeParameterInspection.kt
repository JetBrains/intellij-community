// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.base.psi.isPartOfQualifiedExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.getScopeToSearchParameterReferences
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

private val CONSTRUCTOR_PROPERTY_MODIFIERS_TO_DELETE: Array<IElementType> = KtTokens.VISIBILITY_MODIFIERS.types +
        KtTokens.MODALITY_MODIFIERS.types + KtTokens.LATEINIT_KEYWORD

internal class CanBeParameterInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return parameterVisitor(fun(parameter: KtParameter) {
            // Applicable to val / var parameters of a class / object primary constructors
            val valOrVarKeyword = parameter.valOrVarKeyword?.text ?: return
            if (parameter.hasModifier(KtTokens.OVERRIDE_KEYWORD) || parameter.hasModifier(KtTokens.ACTUAL_KEYWORD)) return
            if (parameter.annotationEntries.isNotEmpty()) return
            val constructor = parameter.parents.match(KtParameterList::class, last = KtPrimaryConstructor::class) ?: return
            val klass = constructor.getContainingClassOrObject() as? KtClass ?: return
            if (klass.isData()) return

            val scopeToSearch = getScopeToSearchParameterReferences(parameter) ?: return
            // Find all references and check them
            val references = ReferencesSearch.search(parameter, scopeToSearch).findAll()
            if (references.isEmpty()) return
            if (references.any { it.element.parent is KtCallableReferenceExpression || it.usedAsPropertyIn(klass) }) return

            if (referencesWithSameNameResolveToNonLocalVariable(klass, parameter)) return

            holder.registerProblem(
                parameter,
                KotlinBundle.message("constructor.parameter.is.never.used.as.a.property"),
                RemoveValVarFix(valOrVarKeyword)
            )
        })
    }

    private fun PsiReference.usedAsPropertyIn(klass: KtClass): Boolean {

        if (this !is KtSimpleNameReference) return true

        val nameExpression = element

        // receiver.x
        val parent = element.parent
        if (parent is KtQualifiedExpression) {
            if (parent.selectorExpression == element) return true
        }

        // x += something
        if (parent is KtBinaryExpression &&
            parent.left == element &&
            KtPsiUtil.isAssignment(parent)
        ) return true

        // init / constructor / non-local property?
        var parameterUser: PsiElement = nameExpression
        do {
            parameterUser = parameterUser.parentOfTypes(
                KtProperty::class,
                KtPropertyAccessor::class,
                KtClassInitializer::class,
                KtFunction::class,
                KtObjectDeclaration::class,
                KtSuperTypeCallEntry::class
            ) ?: return true
        } while (parameterUser is KtProperty && parameterUser.isLocal)

        return when (parameterUser) {
            is KtProperty -> parameterUser.containingClassOrObject !== klass
            is KtClassInitializer -> parameterUser.containingDeclaration !== klass
            is KtFunction, is KtObjectDeclaration, is KtPropertyAccessor -> true
            is KtSuperTypeCallEntry -> parameterUser.getStrictParentOfType<KtClassOrObject>() !== klass
            else -> true
        }
    }

    private fun referencesWithSameNameResolveToNonLocalVariable(klass: KtClass, parameter: KtParameter): Boolean {

        val properties = klass.getProperties().asSequence()

        val initializersAndDelegates = sequence {
            yieldAll(klass.getAnonymousInitializers())
            yieldAll(properties.mapNotNull { it.initializer })
            yieldAll(properties.mapNotNull { it.delegate })
        }

        if (initializersAndDelegates.none()) return false

        analyze(klass) {
            val constructorPropertySymbol =
                (parameter.getSymbol() as? KtValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: return true

            for (element in initializersAndDelegates) {
                val nameReferenceExpressions = element.collectDescendantsOfType<KtNameReferenceExpression> {
                    it.text == parameter.name && !it.isPartOfQualifiedExpression()
                }
                for (nameReferenceExpression in nameReferenceExpressions) {
                    val referenceSymbol = nameReferenceExpression.mainReference.resolveToSymbol() ?: continue
                    if (referenceSymbol != constructorPropertySymbol && referenceSymbol !is KtLocalVariableSymbol) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

private class RemoveValVarFix(private val valOrVarKeyword: String) : PsiUpdateModCommandQuickFix() {

    override fun getName(): String = KotlinBundle.message("remove.0.from.parameter", valOrVarKeyword)

    override fun getFamilyName(): String = KotlinBundle.message("remove.val.or.var.from.parameter")

    override fun applyFix(project: Project, parameter: PsiElement, updater: ModPsiUpdater) {
        if (parameter !is KtParameter) return
        parameter.valOrVarKeyword?.delete()
        // Delete visibility / open / final / lateinit, if any
        // Retain vararg
        val modifierList = parameter.modifierList ?: return
        for (modifier in CONSTRUCTOR_PROPERTY_MODIFIERS_TO_DELETE) {
            if (modifier is KtModifierKeywordToken) {
                modifierList.getModifier(modifier)?.delete()
            }
        }
    }
}