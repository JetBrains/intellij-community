// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ImportAllMembersIntention :
    KotlinApplicableModCommandAction<KtExpression, ImportAllMembersIntention.Context>(KtExpression::class) {

    data class Context(
        val fqName: FqName,
        val shortenCommand: ShortenCommand,
    )

    override fun getFamilyName(): String =
        KotlinBundle.message("import.members.with")

    override fun getPresentation(
        context: ActionContext,
        element: KtExpression,
    ): Presentation? {
        val (fqName) = getElementContext(context, element)
            ?: return null
        return Presentation.of(KotlinBundle.message("import.members.from.0", fqName.asString()))
            .withPriority(PriorityAction.Priority.HIGH)
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean =
        element.isOnTheLeftOfQualificationDot && !element.isInImportDirective()

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val actualReference = element.actualReference
        val target = actualReference?.resolveToSymbol() as? KaNamedClassSymbol ?: return null
        val classId = target.classId ?: return null
        if (!target.origin.isJavaSourceOrLibrary() &&
            (target.classKind == KaClassKind.OBJECT ||
                    // One cannot use on-demand import for properties or functions declared inside objects
                    isReferenceToObjectMemberOrUnresolved(element))
        ) {
            // Import all members of an object is not supported by Kotlin.
            return null
        }
        if (element.getQualifiedExpressionForReceiver()?.isEnumSyntheticMethodCall(target) == true) return null
        if (element.containingKtFile.hasImportedEnumSyntheticMethodCall()) return null

        val shortenCommand = collectPossibleReferenceShortenings(
            element.containingKtFile,
            classShortenStrategy = {
                if (it.classId?.isNestedClassIn(classId) == true) {
                    ShortenStrategy.SHORTEN_AND_STAR_IMPORT
                } else {
                    ShortenStrategy.DO_NOT_SHORTEN
                }
            },
            callableShortenStrategy = {
                if (it.isEnumSyntheticMethodCall(target)) return@collectPossibleReferenceShortenings ShortenStrategy.DO_NOT_SHORTEN
                val containingClassId = if (it is KaConstructorSymbol) {
                    it.containingClassId?.outerClassId
                } else {
                    it.callableId?.classId
                }
                if (containingClassId == classId) {
                    ShortenStrategy.SHORTEN_AND_STAR_IMPORT
                } else {
                    ShortenStrategy.DO_NOT_SHORTEN
                }
            }
        )
        if (shortenCommand.isEmpty) return null
        return Context(classId.asSingleFqName(), shortenCommand)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val shortenCommand = elementContext.shortenCommand
        val file = shortenCommand.targetFile.element ?: return
        removeExistingImportsWhichWillBecomeRedundantAfterAddingStarImports(shortenCommand.starImportsToAdd, file)
        shortenCommand.invokeShortening()
    }

    private fun removeExistingImportsWhichWillBecomeRedundantAfterAddingStarImports(
        starImportsToAdd: Set<FqName>,
        ktFile: KtFile
    ) {
        for (starImportFqName in starImportsToAdd) {
            for (existingImportFromFile in ktFile.importDirectives) {
                if (existingImportFromFile.alias == null
                    && existingImportFromFile.importPath?.fqName?.parent() == starImportFqName
                ) {
                    existingImportFromFile.delete()
                }
            }
        }
    }
}

private fun ClassId.isNestedClassIn(classId: ClassId) =
    packageFqName == classId.packageFqName && relativeClassName.parent() == classId.relativeClassName

private val KtExpression.isOnTheLeftOfQualificationDot: Boolean
    get() {
        return when (val parent = parent) {
            is KtDotQualifiedExpression -> this == parent.receiverExpression
            is KtUserType -> {
                val grandParent = parent.parent as? KtUserType ?: return false
                grandParent.qualifier == parent && parent.referenceExpression == this
            }

            else -> false
        }
    }

private val KtExpression.actualReference: KtReference?
    get() = when (this) {
        is KtDotQualifiedExpression -> selectorExpression?.mainReference ?: mainReference
        else -> mainReference
    }

context(_: KaSession)
private fun isReferenceToObjectMemberOrUnresolved(qualifiedAccess: KtExpression): Boolean {
    val selectorExpression: KtExpression? = qualifiedAccess.getQualifiedExpressionForReceiver()?.selectorExpression
    val referencedSymbol = when (selectorExpression) {
        is KtCallExpression -> selectorExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
        is KtNameReferenceExpression -> selectorExpression.mainReference.resolveToSymbol()
        else -> return false
    } ?: return true
    if (referencedSymbol is KaConstructorSymbol) return false
    return (referencedSymbol.containingDeclaration as? KaClassSymbol)?.classKind?.isObject ?: true
}

private fun KaDeclarationSymbol.isEnum(): Boolean = safeAs<KaClassSymbol>()?.classKind == KaClassKind.ENUM_CLASS

private fun KaCallableSymbol.isEnumSyntheticMethodCall(target: KaNamedClassSymbol): Boolean =
    target.isEnum() && origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED && callableId?.callableName in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES

private fun KtQualifiedExpression.isEnumSyntheticMethodCall(target: KaNamedClassSymbol): Boolean =
    target.isEnum() && canBeReferenceToBuiltInEnumFunction()

context(_: KaSession)
private fun KtFile.hasImportedEnumSyntheticMethodCall(): Boolean = importDirectives.any { importDirective ->
    if (importDirective.importPath?.isAllUnder != true) return false
    val importedEnumFqName = importDirective.importedFqName ?: return false
    if ((importDirective.importedReference?.mainReference?.resolve() as? KtClass)?.isEnum() != true) return false

    fun KtExpression.isFqNameInEnumStaticMethods(): Boolean {
        if (getQualifiedExpressionForSelector() != null) return false
        if (((this as? KtNameReferenceExpression)?.parent as? KtCallableReferenceExpression)?.receiverExpression != null) return false
        val referencedSymbol = when (this) {
            is KtCallExpression -> resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
            is KtNameReferenceExpression -> mainReference.resolveToSymbol()
            else -> return false
        } ?: return false
        val referencedName = (referencedSymbol as? KaCallableSymbol)?.callableId?.callableName ?: return false
        return referencedSymbol.psi?.kotlinFqName == importedEnumFqName && referencedName in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES
    }

    return containingFile.anyDescendantOfType<KtExpression> {
        (it as? KtCallExpression)?.isFqNameInEnumStaticMethods() == true
                || (it as? KtCallableReferenceExpression)?.callableReference?.isFqNameInEnumStaticMethods() == true
                || (it as? KtReferenceExpression)?.isFqNameInEnumStaticMethods() == true
    }
}