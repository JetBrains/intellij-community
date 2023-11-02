// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes.createFromUsage

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

enum class CallableKind {
    FUNCTION,
    CLASS_WITH_PRIMARY_CONSTRUCTOR,
    CONSTRUCTOR,
    PROPERTY;

    fun renderAsString(): String = when(this) {
        FUNCTION -> KotlinBundle.message("text.function.0", 1)
        CONSTRUCTOR -> KotlinBundle.message("text.secondary.constructor")
        PROPERTY -> KotlinBundle.message("text.property.0", 1)
        else -> throw AssertionError("Unexpected callable info: $this")
    }
}

open class CreateCallableFromCallActionFactory {
    companion object {
        val factoryForUnresolvedReferenceDiagnostic = diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) {
            Function.createAvailableQuickFixes(it)
        }
    }

    protected fun getExpressionToCreate(diagnostic: KtFirDiagnostic.UnresolvedReference): KtExpression? {
        val element = diagnostic.psi
        if (element.isPartOfImportDirectiveOrAnnotation()) return null
        val parent = element.parent
        return if (parent is KtCallExpression && parent.calleeExpression == element) parent else element as? KtExpression
    }

    protected class QuickFixTextBuilder(
        private val callableKind: CallableKind,
        private val nameOfUnresolvedSymbol: String,
        private val receiverExpression: KtExpression?,
        private val isAbstract: Boolean,
        private val isExtension: Boolean,
    ) {
        fun build() = buildString {
            append(KotlinBundle.message("text.create"))
            append(' ')
            descriptionOfCallableAsString()?.let { callableKindAsString ->
                append(callableKindAsString)
                append(' ')
            }

            append(callableKind.renderAsString())

            nameOfUnresolvedSymbol.ifEmpty { return@buildString }
            append(" '${renderReceiver()}$nameOfUnresolvedSymbol'")
        }

        private fun descriptionOfCallableAsString(): String? = if (isAbstract) {
            KotlinBundle.message("text.abstract")
        } else if (isExtension) {
            KotlinBundle.message("text.extension")
        } else if (hasReceiver()) {
            KotlinBundle.message("text.member")
        } else null

        private fun hasReceiver() = receiverExpression != null

        private fun renderReceiver(): String {
            val receiverExpression = receiverExpression ?: return ""
            return analyze(receiverExpression) {
                val receiverSymbol = receiverExpression.resolveExpression()
                // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and
                // its type rendering.
                val receiverTypeText = receiverSymbol?.renderAsReceiver() ?: receiverExpression.getKtType()
                    ?.render(KtTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT) ?: receiverExpression.text
                if (isExtension && receiverSymbol is KtCallableSymbol) {
                    val receiverType = receiverSymbol.returnType
                    if (receiverType is KtFunctionalType) "($receiverTypeText)." else "$receiverTypeText."
                } else {
                    receiverTypeText + if (receiverSymbol is KtClassLikeSymbol) ".Companion." else "."
                }
            }
        }

        context (KtAnalysisSession)
        private fun KtSymbol.renderAsReceiver(): String? = when (this) {
            is KtCallableSymbol -> returnType.selfOrSuperTypeWithAbstractMatch()
                ?.render(KtTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
            is KtClassLikeSymbol -> classIdIfNonLocal?.shortClassName?.asString() ?: render(KtDeclarationRendererForSource.WITH_SHORT_NAMES)
            else -> null
        }

        context (KtAnalysisSession)
        private fun KtType.selfOrSuperTypeWithAbstractMatch(): KtType? {
            if (this.hasAbstractDeclaration() == isAbstract) return this
            return getDirectSuperTypes().firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch() }
        }
    }

    object Function: CreateCallableFromCallActionFactory() {
        context (KtAnalysisSession)
        fun createAvailableQuickFixes(diagnostic: KtFirDiagnostic.UnresolvedReference): List<QuickFixActionBase<*>> {
            val callExpression = getExpressionToCreate(diagnostic) as? KtCallExpression ?: return emptyList()
            val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return emptyList()
            if (calleeExpression.getReferencedNameElementType() != KtTokens.IDENTIFIER) return emptyList()
            return callExpression.createQuickFixes(
                calleeExpression, calleeExpression.getReceiverExpression(), calleeExpression.getReferencedName()
            )
        }

        context (KtAnalysisSession)
        private fun KtCallExpression.createQuickFixes(
            calleeExpression: KtSimpleNameExpression, receiver: KtExpression?, nameOfCallableToCreate: String
        ): List<QuickFixActionBase<*>> {
            val defaultQuickFix = createDefaultQuickFix(calleeExpression, receiver, nameOfCallableToCreate)
            val abstractTypeOfContainer = calleeExpression.getAbstractTypeOfReceiver()
            return when (val abstractContainerClass = abstractTypeOfContainer?.convertToClass()) {
                null -> {
                    val quickFixForExtension = createQuickFixForExtension(receiver, nameOfCallableToCreate)
                    listOfNotNull(defaultQuickFix, quickFixForExtension)
                }

                else -> {
                    val quickFixForAbstract =
                        createQuickFixForAbstract(receiver, nameOfCallableToCreate, abstractTypeOfContainer, abstractContainerClass)
                    listOf(defaultQuickFix, quickFixForAbstract)
                }
            }
        }

        context (KtAnalysisSession)
        private fun KtCallExpression.createDefaultQuickFix(
            calleeExpression: KtSimpleNameExpression, receiver: KtExpression?, nameOfCallableToCreate: String
        ): QuickFixActionBase<*> {
            val defaultQuickFixText = QuickFixTextBuilder(
                CallableKind.FUNCTION, nameOfCallableToCreate, receiver, false, false
            ).build()
            return CreateCallableFromUsageFix(
                CallableKind.FUNCTION,
                this,
                defaultQuickFixText,
                buildFunctionDefinitionAsString(nameOfCallableToCreate),
                nameOfCallableToCreate,
                calleeExpression.getReceiverClassOrContainingFile().createSmartPointer()
            )
        }

        context (KtAnalysisSession)
        private fun KtCallExpression.createQuickFixForExtension(
            receiver: KtExpression?, nameOfCallableToCreate: String
        ): QuickFixActionBase<*>? {
            if (receiver == null) return null

            val quickFixText = QuickFixTextBuilder(
                CallableKind.FUNCTION, nameOfCallableToCreate, receiver, false, true
            ).build()
            return LowPriorityCreateCallableFromUsageFix(
                CallableKind.FUNCTION,
                this,
                quickFixText,
                buildFunctionDefinitionAsString(nameOfCallableToCreate),
                nameOfCallableToCreate,
                containingKtFile.createSmartPointer()
            )
        }

        context (KtAnalysisSession)
        private fun KtCallExpression.createQuickFixForAbstract(
            receiver: KtExpression?, nameOfCallableToCreate: String, typeOfContainer: KtType, containerClass: KtClass,
        ): QuickFixActionBase<*> {
            val quickFixText = QuickFixTextBuilder(
                CallableKind.FUNCTION, nameOfCallableToCreate, receiver, true, false
            ).build()
            return CreateCallableFromUsageFix(
                CallableKind.FUNCTION,
                this,
                quickFixText,
                buildFunctionDefinitionAsString(nameOfCallableToCreate, typeOfContainer),
                nameOfCallableToCreate,
                containerClass.createSmartPointer()
            )
        }

        context (KtAnalysisSession)
        private fun KtCallExpression.buildFunctionDefinitionAsString(nameOfFunction: String, containerType: KtType? = null): String =
            buildString {
                if (containerType?.isInterface() == false) append("abstract ")
                val expectedTypeAsString = renderExpectedType()
                append("fun $nameOfFunction(${renderParameters()})${expectedTypeAsString?.let { ": $it" }}")
            }
    }
}

/**
 * Returns the type of the class containing this [KtSimpleNameExpression] if the class is abstract. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtSimpleNameExpression.getAbstractTypeOfContainingClass(): KtType? {
    val containingClass = getStrictParentOfType<KtClassOrObject>() as? KtClass ?: return null
    if (containingClass is KtEnumEntry || containingClass.isAnnotation()) return null

    val classSymbol = containingClass.getSymbol() as? KtClassOrObjectSymbol ?: return null
    val classType = buildClassType(classSymbol)
    if (containingClass.modifierList.hasAbstractModifier() || classSymbol.classKind == KtClassKind.INTERFACE) return classType

    // KtType.getAbstractSuperType() does not guarantee it's the closest abstract super type. We can implement it as a
    // breadth first search, but it can cost a lot in terms of the memory usage.
    return classType.getAbstractSuperType()
}

context (KtAnalysisSession)
private fun KtType.getAbstractSuperType(): KtType? {
    fun List<KtType>.firstAbstractEditableType() = firstOrNull { it.hasAbstractDeclaration() && it.canRefactor() }
    return getDirectSuperTypes().firstAbstractEditableType() ?: getAllSuperTypes().firstAbstractEditableType()
}

/**
 * Returns class or super class of the express's type if the class or the super class is abstract. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtExpression.getTypeOfAbstractSuperClass(): KtType? {
    val type = getKtType() ?: return null
    if (type.hasAbstractDeclaration()) return type
    return type.getAllSuperTypes().firstOrNull { it.hasAbstractDeclaration() }
}

/**
 * Returns the receiver's type if it is abstract, or it has an abstract super class. Otherwise, returns null.
 */
context (KtAnalysisSession)
private fun KtSimpleNameExpression.getAbstractTypeOfReceiver(): KtType? {
    // If no explicit receiver exists, the containing class can be an implicit receiver.
    val receiver = getReceiverExpression() ?: return getAbstractTypeOfContainingClass()
    return receiver.getTypeOfAbstractSuperClass()
}

private fun KtSimpleNameExpression.getReceiverClassOrContainingFile(): KtElement =
    getReceiverExpression()?.getClassOfType() ?: containingKtFile