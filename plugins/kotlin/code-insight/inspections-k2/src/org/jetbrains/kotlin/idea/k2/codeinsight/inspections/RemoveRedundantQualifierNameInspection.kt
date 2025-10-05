// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.collectPossibleReferenceShorteningsInElement
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*

internal class RemoveRedundantQualifierNameInspection : AbstractKotlinInspection() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is KtFile) return null

        val elementsWithRedundantQualifiers = collectPossibleShortenings(file).ifEmpty { return null }

        return elementsWithRedundantQualifiers
            .map { element -> manager.createRedundantQualifierProblemDescriptor(element, isOnTheFly) }
            .toTypedArray()
    }

    private fun collectPossibleShortenings(file: KtFile): List<KtElement> {
        val shortenings = analyze(file) {
            collectShortenings(file)
        }

        val qualifiersToShorten = shortenings.listOfQualifierToShortenInfo.mapNotNull { it.qualifierToShorten.element }
        val typesToShorten = shortenings.listOfTypeToShortenInfo.mapNotNull { it.typeToShorten.element }

        return qualifiersToShorten + typesToShorten
    }

    /**
     * See KTIJ-16225 and KTIJ-15232 for the details about why we have
     * special treatment for enums.
     */
    context(_: KaSession)
    private fun collectShortenings(declaration: KtElement): ShortenCommand =
        collectPossibleReferenceShorteningsInElement(
            declaration,
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

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val elementWithQualifier = descriptor.psiElement ?: return

            when (elementWithQualifier) {
                is KtUserType -> elementWithQualifier.deleteQualifier()
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
