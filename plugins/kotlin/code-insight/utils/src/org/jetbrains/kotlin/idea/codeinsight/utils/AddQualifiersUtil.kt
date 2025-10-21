// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeaf
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import java.lang.RuntimeException

@OptIn(KaContextParameterApi::class)
object AddQualifiersUtil {
    fun addQualifiersRecursively(root: KtElement): KtElement {
        if (root is KtNameReferenceExpression) return applyIfApplicable(root) ?: root

        root.descendantsOfType<KtNameReferenceExpression>()
            .map { it.createSmartPointer() }
            .toList()
            .asReversed()
            .forEach {
                it.element?.let(::applyIfApplicable)
            }

        return root
    }

    context(_: KaSession)
    fun isApplicableTo(referenceExpression: KtNameReferenceExpression, contextSymbol: KaSymbol): Boolean {
        if (referenceExpression.parent is KtInstanceExpressionWithLabel) return false

        val prevElement = referenceExpression.prevLeaf {
            it.elementType !in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
        }
        if (prevElement.elementType == KtTokens.DOT) return false
        val fqName = getFqName(contextSymbol) ?: return false
        if (contextSymbol is KaCallableSymbol && contextSymbol.isExtension || fqName.parent().isRoot == true) return false

        if (prevElement.elementType == KtTokens.COLONCOLON) {

            fun isTopLevelCallable(callableSymbol: KaSymbol): Boolean {
                if (callableSymbol is KaConstructorSymbol) {
                    val containingClassSymbol = callableSymbol.containingDeclaration
                    if (containingClassSymbol?.containingDeclaration == null) {
                        return true
                    }
                }
                return callableSymbol is KaCallableSymbol && callableSymbol.containingDeclaration == null
            }

            if (isTopLevelCallable(contextSymbol)) return false

            val prevSibling = prevElement?.getPrevSiblingIgnoringWhitespaceAndComments()
            if (prevSibling is KtNameReferenceExpression || prevSibling is KtDotQualifiedExpression) return false
        }

        val file = referenceExpression.containingKtFile
        val identifier = referenceExpression.getIdentifier()?.text
        return !file.hasImportAlias() || file.importDirectives.none { it.aliasName == identifier && it.importedFqName == fqName }
    }

    fun applyTo(referenceExpression: KtNameReferenceExpression, fqName: FqName): KtElement {
        val action = {
            val qualifier = fqName.parent().quoteIfNeeded().asString()
            val psiFactory = KtPsiFactory(referenceExpression.project)
            when (val parent = referenceExpression.parent) {
                is KtCallableReferenceExpression -> addOrReplaceQualifier(psiFactory, parent, qualifier)
                is KtCallExpression -> replaceExpressionWithDotQualifier(psiFactory, parent, qualifier)
                is KtUserType -> addQualifierToType(psiFactory, parent, qualifier)
                else -> replaceExpressionWithQualifier(psiFactory, referenceExpression, qualifier, fqName)
            }
        }
        if (referenceExpression.isPhysical) {
            return WriteCommandAction.writeCommandAction(referenceExpression.project).compute<KtElement, RuntimeException> { action() }
        }
        return action()
    }

    fun getFqName(symbol: KaSymbol): FqName? {
        return when (symbol) {
            is KaClassLikeSymbol -> symbol.classId?.asSingleFqName()
            is KaConstructorSymbol -> symbol.containingClassId?.asSingleFqName()
            is KaCallableSymbol -> symbol.callableId?.asSingleFqName()
            else -> null
        }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    private fun applyIfApplicable(referenceExpression: KtNameReferenceExpression): KtElement? {
        val fqName = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(referenceExpression) {
                    val symbol = referenceExpression.mainReference.resolveToSymbols().singleOrNull() as? KaNamedSymbol ?: return null
                    val fqName = getFqName(symbol) ?: return null
                    if (!isApplicableTo(referenceExpression, symbol)) return null
                    fqName
                }
            }
        }
        return applyTo(referenceExpression, fqName)
    }

    private fun addOrReplaceQualifier(factory: KtPsiFactory, expression: KtCallableReferenceExpression, qualifier: String): KtElement {
        val receiver = expression.receiverExpression
        return if (receiver != null) {
            replaceExpressionWithDotQualifier(factory, receiver, qualifier)
        } else {
            val qualifierExpression = factory.createExpression(qualifier)
            expression.addBefore(qualifierExpression, expression.firstChild) as KtElement
        }
    }

    private fun replaceExpressionWithDotQualifier(psiFactory: KtPsiFactory, expression: KtExpression, qualifier: String): KtElement {
        val expressionWithQualifier = psiFactory.createExpressionByPattern("$0.$1", qualifier, expression)
        return expression.replace(expressionWithQualifier) as KtElement
    }

    private fun addQualifierToType(psiFactory: KtPsiFactory, userType: KtUserType, qualifier: String): KtElement {
        val type = userType.parent as? KtNullableType ?: userType
        val typeWithQualifier = psiFactory.createType("$qualifier.${type.text}")
        return type.parent.replace(typeWithQualifier) as KtElement
    }

    private fun replaceExpressionWithQualifier(
        psiFactory: KtPsiFactory,
        referenceExpression: KtNameReferenceExpression,
        packageQualifier: String,
        fqName: FqName
    ): KtElement {
        val fqNameUnsafe = fqName.toUnsafe()
        val shortName = fqNameUnsafe.shortName().asString().quoteIfNeeded()
        val packageSeparator = ".".takeUnless { packageQualifier.isEmpty() } ?: ""
        val expressionWithQualifier = psiFactory.createExpression(packageQualifier + packageSeparator + shortName)
        return referenceExpression.replace(expressionWithQualifier) as KtElement
    }
}
