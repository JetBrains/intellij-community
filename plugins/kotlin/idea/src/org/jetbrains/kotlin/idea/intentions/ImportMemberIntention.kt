// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.imports.canBeAddedToImport
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.ImportDescriptorResult
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ImportMemberIntention : SelfTargetingOffsetIndependentIntention<KtNameReferenceExpression>(
    KtNameReferenceExpression::class.java,
    KotlinBundle.lazyMessage("add.import.for.member")
), HighPriorityAction {
    override fun isApplicableTo(element: KtNameReferenceExpression): Boolean {
        if (element.getQualifiedElement() == element) return false //Ignore simple name expressions

        val qualifiedExpression = element.getQualifiedElement()

        if (element.isInImportDirective()) return false

        val target = target(qualifiedExpression) ?: return false
        val fqName = target.importableFqName ?: return false

        val file = element.containingKtFile
        val project = file.project
        val dummyFile = KtPsiFactory.contextual(file).createFile("Dummy.kt", file.text)
        val helper = ImportInsertHelper.getInstance(project)
        if (helper.importDescriptor(dummyFile, target) == ImportDescriptorResult.FAIL) return false

        setTextGetter(KotlinBundle.lazyMessage("add.import.for.0", fqName.asString()))
        return true
    }

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {

        val qualifiedElement = element.getQualifiedElement() as? KtQualifiedExpression

        // If expression is fqn reference, take full qualified selector, otherwise (Type reference) take element
        val targetElement = qualifiedElement?.selectorExpression?.getQualifiedElementSelector() ?: element

        val targets = targetElement.resolveMainReferenceToDescriptors()
        if (targets.isEmpty()) return

        val fqName = targets.map { it.importableFqName!! }.single()

        val file = targetElement.containingKtFile
        val helper = ImportInsertHelper.getInstance(targetElement.project)
        if (helper.importDescriptor(file, targets.first()) == ImportDescriptorResult.FAIL) return

        val qualifiedExpressions = file.collectDescendantsOfType<KtDotQualifiedExpression> { qualifiedExpression ->
            val selector = qualifiedExpression.getQualifiedElementSelector() as? KtNameReferenceExpression
            selector?.getReferencedNameAsName() == fqName.shortName() && target(qualifiedExpression)?.importableFqName == fqName
        }
        val userTypes = file.collectDescendantsOfType<KtUserType> { userType ->
            val selector = userType.getQualifiedElementSelector() as? KtNameReferenceExpression
            selector?.getReferencedNameAsName() == fqName.shortName() && target(userType)?.importableFqName == fqName
        }

        //TODO: not deep
        ShortenReferences.DEFAULT.process(qualifiedExpressions + userTypes)
    }

    private fun target(qualifiedElement: KtElement): DeclarationDescriptor? {
        val nameExpression = qualifiedElement.getQualifiedElementSelector() as? KtNameReferenceExpression ?: return null
        val receiver = nameExpression.getReceiverExpression() ?: return null
        val bindingContext = qualifiedElement.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
        if (bindingContext[BindingContext.QUALIFIER, receiver] == null) return null

        val targets = nameExpression.mainReference.resolveToDescriptors(bindingContext)
        if (targets.isEmpty()) return null
        if (!targets.all { it.canBeAddedToImport() }) return null
        return targets.singleOrNull()
    }
}
