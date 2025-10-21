// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.ImportAllMembersIntention.Holder.importReceiverMembers
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.ImportDescriptorResult
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ImportAllMembersIntention : SelfTargetingIntention<KtElement>(
    KtElement::class.java,
    KotlinBundle.messagePointer("import.members.with")
), HighPriorityAction {
    override fun isApplicableTo(element: KtElement, caretOffset: Int): Boolean {
        val receiverExpression = element.receiverExpression() ?: return false
        if (!receiverExpression.textRange.containsOffset(caretOffset)) return false
        if (receiverExpression.isInImportDirective()) return false

        val target = target(element, receiverExpression) ?: return false
        val targetFqName = target.importableFqName ?: return false
        if (element.safeAs<KtQualifiedExpression>()?.isEnumSyntheticMethodCall(target) == true) return false

        val file = element.containingKtFile
        if (file.hasImportedEnumSyntheticMethodCall()) return false

        val project = file.project
        val dummyFileText = (file.packageDirective?.text ?: "") + "\n" + (file.importList?.text ?: "")
        val dummyFile = KtPsiFactory.contextual(file).createFile("Dummy.kt", dummyFileText)
        val helper = ImportInsertHelper.getInstance(project)
        if (helper.importDescriptor(dummyFile, target, forceAllUnderImport = true) == ImportDescriptorResult.FAIL) return false

        setTextGetter(KotlinBundle.messagePointer("import.members.from.0", targetFqName.parent().asString()))
        return true
    }

    override fun applyTo(element: KtElement, editor: Editor?) {
        element.importReceiverMembers()
    }

    object Holder {
        fun KtElement.importReceiverMembers() {
            val target = target(this) ?: return
            val classFqName = target.importableFqName!!.parent()

            ImportInsertHelper.getInstance(project).importDescriptor(containingKtFile, target, forceAllUnderImport = true)
            val qualifiedExpressions = containingKtFile.collectDescendantsOfType<KtDotQualifiedExpression> { qualifiedExpression ->
                val qualifierName = qualifiedExpression.receiverExpression.getQualifiedElementSelector() as? KtNameReferenceExpression
                qualifierName?.getReferencedNameAsName() == classFqName.shortName() &&
                        target(qualifiedExpression)?.importableFqName?.parent() == classFqName &&
                        !qualifiedExpression.isEnumSyntheticMethodCall(target)
            }

            val userTypes = containingKtFile.collectDescendantsOfType<KtUserType> { userType ->
                val receiver = userType.receiverExpression()?.getQualifiedElementSelector() as? KtNameReferenceExpression
                receiver?.getReferencedNameAsName() == classFqName.shortName() && target(userType)?.importableFqName
                    ?.parent() == classFqName
            }

            //TODO: not deep
            ShortenReferences.DEFAULT.process(qualifiedExpressions + userTypes)
        }
    }
}

private fun target(qualifiedElement: KtElement, receiverExpression: KtExpression): DeclarationDescriptor? {
    val bindingContext = qualifiedElement.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    if (bindingContext[BindingContext.QUALIFIER, receiverExpression] !is ClassQualifier) {
        return null
    }

    val selector = qualifiedElement.getQualifiedElementSelector() as? KtNameReferenceExpression ?: return null
    return selector.mainReference.resolveToDescriptors(bindingContext).firstOrNull()
}

private fun target(qualifiedElement: KtElement): DeclarationDescriptor? {
    val receiverExpression = qualifiedElement.receiverExpression() ?: return null
    return target(qualifiedElement, receiverExpression)
}

private fun KtElement.receiverExpression(): KtExpression? = when (this) {
    is KtDotQualifiedExpression -> receiverExpression
    is KtUserType -> qualifier?.referenceExpression
    else -> null
}

private fun DeclarationDescriptor.isEnumClass(): Boolean =
    safeAs<ClassDescriptor>()?.kind == ClassKind.ENUM_CLASS

private fun KtQualifiedExpression.isEnumSyntheticMethodCall(receiverDescriptor: DeclarationDescriptor): Boolean =
    receiverDescriptor.containingDeclaration?.isEnumClass() == true && this.canBeReferenceToBuiltInEnumFunction()

private fun KtFile.hasImportedEnumSyntheticMethodCall(): Boolean =
    importDirectives.any { it.isUsedStarImportOfEnumStaticFunctions() }
