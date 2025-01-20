// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.psi.patternMatching.KotlinPsiUnifier
import org.jetbrains.kotlin.idea.util.psi.patternMatching.match
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.isCallee
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.utils.ifEmpty

object RenameUnresolvedReferenceActionFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val ref = diagnostic.psiElement as? KtNameReferenceExpression ?: return null
        return RenameUnresolvedReferenceFix(ref)
    }
}

class RenameUnresolvedReferenceFix(element: KtNameReferenceExpression) : AbstractRenameUnresolvedReferenceFix(element) {
    override fun KtExpression.findOccurrences(container: KtElement, isCallee: Boolean): List<KtNameReferenceExpression> = toRange()
        .match(container, KotlinPsiUnifier.DEFAULT)
        .mapNotNull {
            val candidate = (it.range.elements.first() as? KtExpression)?.getQualifiedElementSelector() as? KtNameReferenceExpression
            if (candidate != null && candidate.isCallee() == isCallee) candidate else null
        }

    override fun KtExpression.getTargetCandidates(element: KtNameReferenceExpression): Array<LookupElementBuilder> {
         val resolutionFacade = element.getResolutionFacade()
        val context = resolutionFacade.analyze(element, BodyResolveMode.PARTIAL_WITH_CFA)
        val moduleDescriptor = resolutionFacade.moduleDescriptor
        val variantsHelper = ReferenceVariantsHelper(context, resolutionFacade, moduleDescriptor, {
            it !is DeclarationDescriptorWithVisibility || it.isVisible(element, null, context, resolutionFacade)
        }, NotPropertiesService.getNotProperties(element))
        val expectedTypes = guessTypes(context, moduleDescriptor)
            .ifEmpty { arrayOf(moduleDescriptor.builtIns.nullableAnyType) }
        val descriptorKindFilter = if (element.isCallee()) DescriptorKindFilter.FUNCTIONS else DescriptorKindFilter.VARIABLES
        val originalName = element.getReferencedName()
        val lookupItems = variantsHelper.getReferenceVariants(element, descriptorKindFilter, { true })
            .filter { candidate ->
                candidate is CallableDescriptor && (expectedTypes.any { candidate.returnType?.isSubtypeOf(it) ?: false })
            }
            .mapTo(if (isUnitTestMode()) linkedSetOf() else linkedSetOf(originalName)) {
                it.name.asString()
            }
            .map { LookupElementBuilder.create(it) }
            .toTypedArray()
        return lookupItems
    }
}
