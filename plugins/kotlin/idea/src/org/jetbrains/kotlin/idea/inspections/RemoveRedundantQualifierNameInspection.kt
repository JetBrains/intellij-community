// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.*
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionWithoutClassInstanceAsReceiver
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.core.unwrapIfFakeOverride
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.isReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.scopes.utils.findFirstClassifierWithDeprecationStatus
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.JComponent

class RemoveRedundantQualifierNameInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    /**
     * In order to detect that `foo()` and `GrandBase.foo()` point to the same method,
     * we need to unwrap fake overrides from descriptors. If we don't do that, they will
     * have different `fqName`s, and the inspection will not detect `GrandBase` as a
     * redundant qualifier.
     */
    var unwrapFakeOverrides: Boolean = false

  override fun getOptionsPane() = pane(
    checkbox(::unwrapFakeOverrides.name, KotlinBundle.message("redundant.qualifier.unnecessary.non.direct.parent.class.qualifier")))

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                val expressionParent = expression.parent
                if (expressionParent is KtDotQualifiedExpression || expressionParent is KtPackageDirective || expressionParent is KtImportDirective) return
                var expressionForAnalyze = expression.expressionWithoutClassInstanceAsReceiver() ?: return
                if (expressionForAnalyze.selectorExpression?.text == expressionParent.getNonStrictParentOfType<KtProperty>()?.name) return

                val context = expression.safeAnalyzeNonSourceRootCode()
                val receiver = expressionForAnalyze.receiverExpression
                val receiverReference = receiver.declarationDescriptor(context)
                var hasCompanion = false
                var callingBuiltInEnumFunction = false
                when {
                    receiverReference.isEnumCompanionObject() -> when (receiver) {
                        is KtDotQualifiedExpression -> {
                            if (receiver.receiverExpression.declarationDescriptor(context).isEnumClass()) return
                            expressionForAnalyze = receiver
                        }
                        else -> return
                    }
                    receiverReference.isEnumClass() -> {
                        hasCompanion = expressionForAnalyze.selectorExpression?.declarationDescriptor(context).isEnumCompanionObject()
                        callingBuiltInEnumFunction = expressionForAnalyze.isReferenceToBuiltInEnumFunction()
                        when {
                            receiver is KtDotQualifiedExpression -> expressionForAnalyze = receiver
                            hasCompanion || callingBuiltInEnumFunction -> return
                        }
                    }
                }

                val originalExpression: KtExpression = expressionForAnalyze.parent as? KtClassLiteralExpression ?: expressionForAnalyze
                val originalDescriptor = expressionForAnalyze.getQualifiedElementSelector()?.declarationDescriptor(context)?.let {
                    if (hasCompanion || callingBuiltInEnumFunction) it.safeAs<LazyClassDescriptor>()?.companionObjectDescriptor else it
                } ?: return

                val applicableExpression = expressionForAnalyze.firstApplicableExpression(
                    validator = {
                        applicableExpression(originalExpression, context, originalDescriptor, receiverReference, unwrapFakeOverrides)
                    },
                    generator = { firstChild as? KtDotQualifiedExpression }
                ) ?: return

                reportProblem(holder, applicableExpression)
            }

            override fun visitUserType(type: KtUserType) {
                if (type.parent is KtUserType) return

                val context = type.safeAnalyzeNonSourceRootCode()
                val applicableExpression = type.firstApplicableExpression(
                    validator = { applicableExpression(context) },
                    generator = { firstChild as? KtUserType }
                ) ?: return

                reportProblem(holder, applicableExpression)
            }
        }
}

private fun KtElement.declarationDescriptor(context: BindingContext): DeclarationDescriptor? =
    (safeAs<KtQualifiedExpression>()?.selectorExpression ?: this).mainReference?.resolveToDescriptors(context)?.firstOrNull()

private fun DeclarationDescriptor?.isEnumClass() = safeAs<ClassDescriptor>()?.kind == ClassKind.ENUM_CLASS

private fun DeclarationDescriptor?.isEnumCompanionObject() = this?.isCompanionObject() == true && containingDeclaration.isEnumClass()

private tailrec fun <T : KtElement> T.firstApplicableExpression(validator: T.() -> T?, generator: T.() -> T?): T? {
    ProgressManager.checkCanceled()
    return validator() ?: generator()?.firstApplicableExpression(validator, generator)
}

private fun KtDotQualifiedExpression.applicableExpression(
    originalExpression: KtExpression,
    oldContext: BindingContext,
    originalDescriptor: DeclarationDescriptor,
    receiverReference: DeclarationDescriptor?,
    unwrapFakeOverrides: Boolean
): KtDotQualifiedExpression? {
    if (!receiverExpression.isApplicableReceiver(oldContext) || !ShortenReferences.canBePossibleToDropReceiver(this, oldContext)) {
        return null
    }

    val expressionText = originalExpression.text.substring(lastChild.startOffset - originalExpression.startOffset)
    val newExpression = KtPsiFactory(project).createExpressionIfPossible(expressionText) ?: return null
    val newContext = newExpression.analyzeAsReplacement(originalExpression, oldContext)
    val newDescriptor = newExpression.selector()?.declarationDescriptor(newContext) ?: return null

    fun DeclarationDescriptor.unwrapFakeOverrideIfNecessary(): DeclarationDescriptor {
        return if (unwrapFakeOverrides) this.unwrapIfFakeOverride() else this
    }

    val originalDescriptorFqName = originalDescriptor.unwrapFakeOverrideIfNecessary().fqNameSafe
    val newDescriptorFqName = newDescriptor.unwrapFakeOverrideIfNecessary().fqNameSafe
    if (originalDescriptorFqName != newDescriptorFqName) return null

    if (newExpression is KtQualifiedExpression && !compareDescriptors(
            project,
            newExpression.receiverExpression.declarationDescriptor(newContext)?.containingDeclaration,
            receiverReference?.containingDeclaration
        )
    ) return null

    return this.takeIf {
        if (newDescriptor is ImportedFromObjectCallableDescriptor<*>)
            compareDescriptors(project, newDescriptor.callableFromObject, originalDescriptor)
        else
            compareDescriptors(project, newDescriptor, originalDescriptor)
    }
}

private fun KtExpression.selector(): KtElement? = if (this is KtClassLiteralExpression) receiverExpression?.getQualifiedElementSelector()
else getQualifiedElementSelector()

private fun KtExpression.isApplicableReceiver(context: BindingContext): Boolean {
    if (this is KtInstanceExpressionWithLabel) return false

    val reference = getQualifiedElementSelector()
    val descriptor = reference?.declarationDescriptor(context) ?: return false

    return if (!descriptor.isCompanionObject()) true
    else descriptor.name.asString() != reference.text
}

private fun KtUserType.applicableExpression(context: BindingContext): KtUserType? {
    if (firstChild !is KtUserType) return null
    val referenceExpression = referenceExpression as? KtNameReferenceExpression ?: return null
    val originalDescriptor = referenceExpression.declarationDescriptor(context)?.let {
        it.safeAs<ClassConstructorDescriptor>()?.containingDeclaration ?: it
    } ?: return null

    if (originalDescriptor is ClassDescriptor
        && originalDescriptor.isInner
        && (originalDescriptor.containingDeclaration as? ClassDescriptor)?.typeConstructor != null
    ) return null

    val shortName = originalDescriptor.importableFqName?.shortName() ?: return null
    val scope = referenceExpression.getResolutionScope(context) ?: return null
    val descriptor = scope.findFirstClassifierWithDeprecationStatus(shortName, NoLookupLocation.FROM_IDE)?.descriptor ?: return null
    return if (descriptor == originalDescriptor) this else null
}

private fun reportProblem(holder: ProblemsHolder, element: KtElement) {
    val firstChild = element.firstChild
    holder.registerProblem(
        element,
        KotlinBundle.message("redundant.qualifier.name"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        TextRange.from(firstChild.startOffsetInParent, firstChild.textLength + 1),
        RemoveRedundantQualifierNameQuickFix()
    )
}

class RemoveRedundantQualifierNameQuickFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.qualifier.name.quick.fix.text")
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as KtFile
        val range = when (val element = descriptor.psiElement) {
            is KtUserType -> IntRange(element.startOffset, element.endOffset)
            is KtDotQualifiedExpression -> {
                val selectorReference = element.selectorExpression?.declarationDescriptor(element.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL))
                val endOffset = if (selectorReference.isEnumClass() || selectorReference.isEnumCompanionObject()) {
                    element.endOffset
                } else {
                    element.getLastParentOfTypeInRowWithSelf<KtDotQualifiedExpression>()?.getQualifiedElementSelector()?.endOffset ?: return
                }
                IntRange(element.startOffset, endOffset)
            }
            else -> IntRange.EMPTY
        }

        val substring = file.text.substring(range.first, range.last)
        Regex.fromLiteral(substring).findAll(file.text, file.importList?.endOffset ?: 0).toList().asReversed().forEach {
            ShortenReferences.DEFAULT.process(file, it.range.first, it.range.last + 1)
        }
    }
}
