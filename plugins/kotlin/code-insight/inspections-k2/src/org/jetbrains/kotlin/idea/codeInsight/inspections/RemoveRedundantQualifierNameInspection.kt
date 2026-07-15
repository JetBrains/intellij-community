// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.importableFqName
import org.jetbrains.kotlin.idea.base.analysis.api.utils.ShortenCommandForIde
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectPossibleReferenceShorteningsInElementForIde
import org.jetbrains.kotlin.idea.base.analysis.isNotInjectedOrShouldBeAnalyzed
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenOptionsForIde
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNonPublicApi
import org.jetbrains.kotlin.psi.KtPsiMutationService
import org.jetbrains.kotlin.psi.KtUserType

internal class RemoveRedundantQualifierNameInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun isAvailableForFile(file: PsiFile): Boolean =
        file.isNotInjectedOrShouldBeAnalyzed

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is KtFile) return null

        val elementsWithRedundantQualifiers = collectPossibleShortenings(file).ifEmpty { return null }

        return elementsWithRedundantQualifiers
            .map { element -> manager.createRedundantQualifierProblemDescriptor(element, isOnTheFly) }
            .toTypedArray()
    }

    private fun collectPossibleShortenings(file: KtFile): List<KtElement> = analyze(file) {
        val shortenings = collectShortenings(file)

        val qualifiersToShorten = shortenings.listOfQualifierToShortenInfo.mapNotNull { it.qualifierToShorten.element }
        val typesToShorten = shortenings.listOfTypeToShortenInfo.mapNotNull { it.typeToShorten.element }

        return (qualifiersToShorten + typesToShorten)
            .filter {  shorteningPreservesResolve(it)  }
    }

    /**
     * See KTIJ-16225 and KTIJ-15232 for the details about why we have
     * special treatment for enums.
     */
    context(_: KaSession)
    private fun collectShortenings(declaration: KtElement): ShortenCommandForIde =
        collectPossibleReferenceShorteningsInElementForIde(
            declaration,
            shortenOptions = ShortenOptionsForIde.DEFAULT.copy(
                removeExplicitCompanionReferences = false,
                // Highlighting CSR qualifiers as redundant would be too noisy; see KTIJ-38137
                removeContextSensitiveResolutionQualifiers = false,
            ),
            classShortenStrategy = { classSymbol ->
                if (classSymbol.isEnumCompanionObject()) {
                    ShortenStrategy.DO_NOT_SHORTEN
                } else {
                    ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED
                }
            },
            callableShortenStrategy = { callableSymbol ->
                val containingSymbol = callableSymbol.containingDeclaration

                if (callableSymbol !is KaEnumEntrySymbol && (containingSymbol.isEnumClass() || containingSymbol.isEnumCompanionObject())) {
                    ShortenStrategy.DO_NOT_SHORTEN
                } else {
                    ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED
                }
            },
        )

    context(_: KaSession)
    private fun shorteningPreservesResolve(element: KtElement): Boolean {
        val reference = when (element) {
            is KtDotQualifiedExpression -> element.selectorExpression as? KtNameReferenceExpression
            is KtUserType -> element.referenceExpression as? KtNameReferenceExpression
            else -> null
        } ?: return true
        val intendedFqName = reference.mainReference.resolveToSymbol()?.importableFqName ?: return true

        return element.containingKtFile.importDirectives.none { import ->
            import.aliasName != null && import.importedFqName == intendedFqName
        }
    }

    private fun InspectionManager.createRedundantQualifierProblemDescriptor(element: KtElement, isOnTheFly: Boolean): ProblemDescriptor {
        val qualifierToRemove = element.getQualifier()

        return createProblemDescriptor(
            element,
            qualifierToRemove?.textRangeIn(element),
            KotlinBundle.message("remove.redundant.qualifier.name.quick.fix.text"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly,
            RemoveQualifierQuickFix,
        )
    }

    private object RemoveQualifierQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.qualifier.name.quick.fix.text")

        @OptIn(KtNonPublicApi::class)
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val elementWithQualifier = descriptor.psiElement ?: return

            when (elementWithQualifier) {
                is KtUserType if (elementWithQualifier.qualifier != null) -> KtPsiMutationService.getInstance()
                    .removeQualifier(elementWithQualifier)

                is KtDotQualifiedExpression -> elementWithQualifier.deleteQualifier()
            }
        }
    }
}

context(_: KaSession)
private fun KaDeclarationSymbol.getContainingClassForCompanionObject(): KaNamedClassSymbol? {
    if (this !is KaClassSymbol || this.classKind != KaClassKind.COMPANION_OBJECT) return null

    val containingClass = containingDeclaration as? KaNamedClassSymbol
    return containingClass?.takeIf { it.companionObject == this }
}

private fun KaDeclarationSymbol?.isEnumClass(): Boolean {
    val classSymbol = this as? KaClassSymbol ?: return false
    return classSymbol.classKind == KaClassKind.ENUM_CLASS
}

context(_: KaSession)
private fun KaDeclarationSymbol?.isEnumCompanionObject(): Boolean =
    this?.getContainingClassForCompanionObject().isEnumClass()

private fun KtDotQualifiedExpression.deleteQualifier(): KtExpression? {
    val selectorExpression = selectorExpression ?: return null
    return this.replace(selectorExpression) as KtExpression
}

private fun KtElement.getQualifier(): KtElement? = when (this) {
    is KtUserType -> qualifier
    is KtDotQualifiedExpression -> receiverExpression
    else -> null
}
