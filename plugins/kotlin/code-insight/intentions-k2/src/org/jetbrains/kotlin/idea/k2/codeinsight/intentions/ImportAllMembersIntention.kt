// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ImportAllMembersIntention :
    AbstractKotlinApplicableIntentionWithContext<KtExpression, ImportAllMembersIntention.Context>(KtExpression::class),
    HighPriorityAction {

    class Context(
        val fqName: FqName,
        val shortenCommand: ShortenCommand,
    )

    override fun getFamilyName(): String = KotlinBundle.message("import.members.with")

    override fun getActionName(element: KtExpression, context: Context): String =
        KotlinBundle.message("import.members.from.0", context.fqName.asString())

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtExpression): Boolean =
        element.isOnTheLeftOfQualificationDot && !element.isInImportDirective()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtExpression): Context? {
        val target = element.actualReference?.resolveToSymbol() as? KtNamedClassOrObjectSymbol ?: return null
        val classId = target.classIdIfNonLocal ?: return null
        if (target.origin != KtSymbolOrigin.JAVA &&
            (target.classKind == KtClassKind.OBJECT ||
                    // One cannot use on-demand import for properties or functions declared inside objects
                    isReferenceToObjectMemberOrUnresolved(element))
        ) {
            // Import all members of an object is not supported by Kotlin.
            return null
        }
        if (element.getQualifiedExpressionForReceiver()?.isEnumSyntheticMethodCall(target) == true) return null
        with (this@KtAnalysisSession) {
            if (element.containingKtFile.hasImportedEnumSyntheticMethodCall()) return null
        }

        val shortenCommand = collectPossibleReferenceShortenings(
            element.containingKtFile,
            classShortenOption = {
                if (it.classIdIfNonLocal?.isNestedClassIn(classId) == true) {
                    ShortenOption.SHORTEN_AND_STAR_IMPORT
                } else {
                    ShortenOption.DO_NOT_SHORTEN
                }
            },
            callableShortenOption = {
                if (it.isEnumSyntheticMethodCall(target)) return@collectPossibleReferenceShortenings ShortenOption.DO_NOT_SHORTEN
                val containingClassId = if (it is KtConstructorSymbol) {
                    it.containingClassIdIfNonLocal?.outerClassId
                } else {
                    it.callableIdIfNonLocal?.classId
                }
                if (containingClassId == classId) {
                    ShortenOption.SHORTEN_AND_STAR_IMPORT
                } else {
                    ShortenOption.DO_NOT_SHORTEN
                }
            }
        )
        if (shortenCommand.isEmpty) return null
        return Context(classId.asSingleFqName(), shortenCommand)
    }

    override fun apply(element: KtExpression, context: Context, project: Project, editor: Editor?) {
        context.shortenCommand.invokeShortening()
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

private fun KtAnalysisSession.isReferenceToObjectMemberOrUnresolved(qualifiedAccess: KtExpression): Boolean {
    val selectorExpression: KtExpression? = qualifiedAccess.getQualifiedExpressionForReceiver()?.selectorExpression
    val referencedSymbol = when (selectorExpression) {
        is KtCallExpression -> selectorExpression.resolveCall().successfulCallOrNull<KtCallableMemberCall<*, *>>()?.symbol
        is KtNameReferenceExpression -> selectorExpression.mainReference.resolveToSymbol()
        else -> return false
    } as? KtSymbolWithKind ?: return true
    if (referencedSymbol is KtConstructorSymbol) return false
    return (referencedSymbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind?.isObject ?: true
}

private fun KtDeclarationSymbol.isEnum(): Boolean = safeAs<KtClassOrObjectSymbol>()?.classKind == KtClassKind.ENUM_CLASS

private fun KtCallableSymbol.isEnumSyntheticMethodCall(target: KtNamedClassOrObjectSymbol): Boolean =
    target.isEnum() && origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED && callableIdIfNonLocal?.callableName in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES

private fun KtQualifiedExpression.isEnumSyntheticMethodCall(target: KtNamedClassOrObjectSymbol): Boolean =
    target.isEnum() && canBeReferenceToBuiltInEnumFunction()

context(KtAnalysisSession)
private fun KtFile.hasImportedEnumSyntheticMethodCall(): Boolean = importDirectives.any { importDirective ->
    if (importDirective.importPath?.isAllUnder != true) return false
    val importedEnumFqName = importDirective.importedFqName ?: return false
    if ((importDirective.importedReference?.mainReference?.resolve() as? KtClass)?.isEnum() != true) return false

    fun KtExpression.isFqNameInEnumStaticMethods(): Boolean {
        if (getQualifiedExpressionForSelector() != null) return false
        if (((this as? KtNameReferenceExpression)?.parent as? KtCallableReferenceExpression)?.receiverExpression != null) return false
        val referencedSymbol = when (this) {
            is KtCallExpression -> resolveCall().successfulCallOrNull<KtCallableMemberCall<*, *>>()?.symbol
            is KtNameReferenceExpression -> mainReference.resolveToSymbol()
            else -> return false
        } ?: return false
        val referencedName = (referencedSymbol as? KtCallableSymbol)?.callableIdIfNonLocal?.callableName ?: return false
        return referencedSymbol.psi?.kotlinFqName == importedEnumFqName && referencedName in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES
    }

    return containingFile.anyDescendantOfType<KtExpression> {
        (it as? KtCallExpression)?.isFqNameInEnumStaticMethods() == true
                || (it as? KtCallableReferenceExpression)?.callableReference?.isFqNameInEnumStaticMethods() == true
                || (it as? KtReferenceExpression)?.isFqNameInEnumStaticMethods() == true
    }
}