// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.move.OuterInstanceReferenceUsageInfo
import org.jetbrains.kotlin.idea.refactoring.move.collectOuterInstanceReferences
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.*
import org.jetbrains.kotlin.idea.refactoring.move.traverseOuterInstanceReferences
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchOverriders
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.util.findCallableMemberBySignature
import java.util.*

class MoveMemberToCompanionObjectIntention : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.lazyMessage("move.to.companion.object")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtNamedFunction && element !is KtProperty && element !is KtClassOrObject) return null
        if (element is KtEnumEntry) return null
        if (element is KtNamedFunction && element.bodyExpression == null) return null
        if (element is KtNamedFunction && element.valueParameterList == null) return null
        if ((element is KtNamedFunction || element is KtProperty) && element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null
        val containingClass = element.containingClassOrObject as? KtClass ?: return null
        if (containingClass.isLocal || containingClass.isInner()) return null

        val nameIdentifier = element.nameIdentifier ?: return null
        if (element is KtProperty && element.hasModifier(KtTokens.CONST_KEYWORD) && !element.isVar) {
            val constElement = element.modifierList?.allChildren?.find { it.node.elementType == KtTokens.CONST_KEYWORD }
            if (constElement != null) return TextRange(constElement.startOffset, nameIdentifier.endOffset)
        }
        return nameIdentifier.textRange
    }

    class JavaUsageInfo(refExpression: PsiReferenceExpression) : UsageInfo(refExpression)

    class ImplicitReceiverUsageInfo(refExpression: KtSimpleNameExpression, val callExpression: KtExpression) : UsageInfo(refExpression)
    class ExplicitReceiverUsageInfo(refExpression: KtSimpleNameExpression, val receiverExpression: KtExpression) : UsageInfo(refExpression)

    private fun getNameSuggestionsForOuterInstance(element: KtNamedDeclaration): List<String> {
        val containingClass = element.containingClassOrObject as KtClass
        val containingClassDescriptor = containingClass.unsafeResolveToDescriptor() as ClassDescriptorWithResolutionScopes
        val companionDescriptor = containingClassDescriptor.companionObjectDescriptor
        val companionMemberScope = (companionDescriptor ?: containingClassDescriptor).scopeForMemberDeclarationResolution
        val validator = CollectingNameValidator(element.getValueParameters().mapNotNull { it.name }) {
            companionMemberScope.getContributedVariables(Name.identifier(it), NoLookupLocation.FROM_IDE).isEmpty()
        }

        return Fe10KotlinNameSuggester.suggestNamesByType(containingClassDescriptor.defaultType, validator, "receiver")
    }

    private fun runTemplateForInstanceParam(
        declaration: KtNamedDeclaration,
        nameSuggestions: List<String>,
        editor: Editor?
    ) {
        if (nameSuggestions.isNotEmpty() && editor != null) {
            val restoredElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration) ?: return
            val restoredParam = restoredElement.getValueParameters().first()

            val paramRefs = ReferencesSearch.search(restoredParam, LocalSearchScope(restoredElement)).toList()

            editor.caretModel.moveToOffset(restoredElement.startOffset)

            val templateBuilder = TemplateBuilderImpl(restoredElement)
            templateBuilder.replaceElement(restoredParam.nameIdentifier!!, "ParamName", ChooseStringExpression(nameSuggestions), true)
            paramRefs.forEach { templateBuilder.replaceElement(it, "ParamName", "ParamRef", false) }
            templateBuilder.run(editor, true)
        }
    }

    private fun moveReceiverToArgumentList(refElement: PsiElement, classFqName: FqName) {
        when (refElement) {
            is PsiReferenceExpression -> {
                val qualifier = refElement.qualifier
                val call = refElement.parent as? PsiMethodCallExpression
                if (call != null && qualifier != null) {
                    val argumentList = call.argumentList
                    argumentList.addBefore(qualifier, argumentList.expressions.firstOrNull())
                }
            }

            is KtSimpleNameExpression -> {
                val call = refElement.parent as? KtCallExpression ?: return
                val psiFactory = KtPsiFactory(refElement.project)
                val argumentList =
                    call.valueArgumentList
                        ?: call.addAfter(psiFactory.createCallArguments("()"), call.typeArgumentList ?: refElement) as KtValueArgumentList

                val receiver = call.getQualifiedExpressionForSelector()?.receiverExpression
                val receiverArg = receiver?.let { psiFactory.createArgument(it) }
                    ?: psiFactory.createArgument(psiFactory.createExpression("this@${classFqName.shortName().asString()}"))
                argumentList.addArgumentBefore(receiverArg, argumentList.arguments.firstOrNull())
            }
        }
    }

    fun retrieveConflictsAndUsages(
        project: Project, editor: Editor?, element: KtNamedDeclaration, containingClass: KtClass
    ): Triple<MultiMap<PsiElement, String>, List<UsageInfo>, List<UsageInfo>>? {
        val description = RefactoringUIUtil.getDescription(element, false)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        if (HierarchySearchRequest(element, element.useScope, false).searchOverriders().any()) {
            CommonRefactoringUtil.showErrorHint(
                project, editor, KotlinBundle.message("0.is.overridden.by.declaration.s.in.a.subclass", description), text, null
            )
            return null
        }

        if (hasTypeParameterReferences(containingClass, element)) {
            CommonRefactoringUtil.showErrorHint(
                project, editor, KotlinBundle.message("0.references.type.parameters.of.the.containing.class", description), text, null
            )
            return null
        }

        val externalUsages = SmartList<UsageInfo>()
        val outerInstanceUsages = SmartList<UsageInfo>()
        val conflicts = MultiMap<PsiElement, String>()

        containingClass.companionObjects.firstOrNull()?.let { companion ->
            val companionDescriptor = companion.unsafeResolveToDescriptor() as ClassDescriptor
            val callableDescriptor = element.unsafeResolveToDescriptor() as CallableMemberDescriptor
            companionDescriptor.findCallableMemberBySignature(callableDescriptor)?.let {
                DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)
            }?.let {
                conflicts.putValue(
                    it,
                    KotlinBundle.message("companion.object.already.contains.0", RefactoringUIUtil.getDescription(it, false))
                )
            }
        }

        val outerInstanceReferences = collectOuterInstanceReferences(element)
        if (outerInstanceReferences.isNotEmpty()) {
            if (element is KtProperty) {
                conflicts.putValue(
                    element,
                    KotlinBundle.message(
                        "usages.of.outer.class.instance.inside.of.property.0.won.t.be.processed",
                        element.name.toString()
                    )
                )
            } else {
                outerInstanceReferences.filterNotTo(outerInstanceUsages) { it.reportConflictIfAny(conflicts) }
            }
        }

        project.runSynchronouslyWithProgress(KotlinBundle.message("searching.for.0", element.name.toString()), true) {
            runReadAction {
                ReferencesSearch.search(element, element.useScope).mapNotNullTo(externalUsages) { ref ->
                    ProgressManager.checkCanceled()
                    when (ref) {
                        is PsiReferenceExpression -> JavaUsageInfo(ref)
                        is KtSimpleNameReference -> {
                            val refExpr = ref.expression
                            if (element.isAncestor(refExpr)) return@mapNotNullTo null
                            val resolvedCall = refExpr.resolveToCall() ?: return@mapNotNullTo null

                            val callExpression = resolvedCall.call.callElement as? KtExpression ?: return@mapNotNullTo null

                            val extensionReceiver = resolvedCall.extensionReceiver
                            if (extensionReceiver != null && extensionReceiver !is ImplicitReceiver) {
                                conflicts.putValue(
                                    callExpression,
                                    KotlinBundle.message(
                                        "calls.with.explicit.extension.receiver.won.t.be.processed.0",
                                        callExpression.text
                                    )
                                )
                                return@mapNotNullTo null
                            }

                            val dispatchReceiver = resolvedCall.dispatchReceiver ?: return@mapNotNullTo null
                            if (dispatchReceiver is ExpressionReceiver) {
                                ExplicitReceiverUsageInfo(refExpr, dispatchReceiver.expression)
                            } else {
                                ImplicitReceiverUsageInfo(refExpr, callExpression)
                            }
                        }
                        else -> null
                    }
                }
            }
        }
        return Triple(conflicts, externalUsages, outerInstanceUsages)
    }

    fun doMove(
        progressIndicator: ProgressIndicator,
        element: KtNamedDeclaration,
        externalUsages: List<UsageInfo>,
        outerInstanceUsages: List<UsageInfo>,
        editor: Editor?
    ): KtNamedDeclaration {
        progressIndicator.isIndeterminate = false
        progressIndicator.text = KotlinBundle.message("moving.to.companion.object")
        val totalCount = externalUsages.size + outerInstanceUsages.size + 1
        val project = element.project
        val containingClass = element.containingClassOrObject as KtClass

        val javaCodeStyleManager = JavaCodeStyleManager.getInstance(project)

        val companionObject = containingClass.getOrCreateCompanionObject()
        val companionLightClass = companionObject.toLightClass()

        val ktPsiFactory = KtPsiFactory(project)
        val javaPsiFactory = JavaPsiFacade.getInstance(project).elementFactory
        val javaCompanionRef = companionLightClass?.let { javaPsiFactory.createReferenceExpression(it) }
        val ktCompanionRef = ktPsiFactory.createExpression(companionObject.fqName!!.asString())

        val elementsToShorten = SmartList<KtElement>()

        val nameSuggestions: List<String>
        progressIndicator.checkCanceled()
        if (outerInstanceUsages.isNotEmpty() && element is KtNamedFunction) {
            val parameterList = element.valueParameterList!!
            val parameters = parameterList.parameters

            val newParamType = (containingClass.unsafeResolveToDescriptor() as ClassDescriptor).defaultType

            nameSuggestions = getNameSuggestionsForOuterInstance(element)

            val newParam = parameterList.addParameterBefore(
                ktPsiFactory.createParameter(
                    "${nameSuggestions.first()}: ${IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(newParamType)}"
                ),
                parameters.firstOrNull()
            )

            val newOuterInstanceRef = ktPsiFactory.createExpression(newParam.name!!)
            for ((index, usage) in outerInstanceUsages.withIndex()) {
                progressIndicator.checkCanceled()
                progressIndicator.fraction = index * 1.0 / totalCount
                when (usage) {
                    is OuterInstanceReferenceUsageInfo.ExplicitThis -> {
                        usage.expression?.replace(newOuterInstanceRef)
                    }

                    is OuterInstanceReferenceUsageInfo.ImplicitReceiver -> {
                        usage.callElement?.let { it.replace(ktPsiFactory.createExpressionByPattern("$0.$1", newOuterInstanceRef, it)) }
                    }
                }
            }
        } else {
            nameSuggestions = emptyList()
        }

        val hasInstanceArg = nameSuggestions.isNotEmpty()

        removeModifiers(element)

        val newDeclaration = Mover.Default(element, companionObject)
        progressIndicator.checkCanceled()
        for ((index, usage) in externalUsages.withIndex()) {
            progressIndicator.checkCanceled()
            progressIndicator.fraction = (outerInstanceUsages.size + index) * 1.0 / totalCount
            val usageElement = usage.element ?: continue

            if (hasInstanceArg) {
                moveReceiverToArgumentList(usageElement, containingClass.fqName!!)
            }

            when (usage) {
                is JavaUsageInfo -> {
                    if (javaCompanionRef != null) {
                        (usageElement as? PsiReferenceExpression)
                            ?.qualifierExpression
                            ?.replace(javaCompanionRef)
                            ?.let { javaCodeStyleManager.shortenClassReferences(it) }
                    }
                }

                is ExplicitReceiverUsageInfo -> {
                    elementsToShorten += usage.receiverExpression.replaced(ktCompanionRef)
                }

                is ImplicitReceiverUsageInfo -> {
                    usage.callExpression
                        .let { it.replaced(ktPsiFactory.createExpressionByPattern("$0.$1", ktCompanionRef, it)) }
                        .let {
                            val qualifiedCall = it as KtQualifiedExpression
                            elementsToShorten += qualifiedCall.receiverExpression
                            if (hasInstanceArg) {
                                elementsToShorten += (qualifiedCall.selectorExpression as KtCallExpression).valueArguments.first()
                            }
                        }
                }
            }
        }
        progressIndicator.checkCanceled()
        ShortenReferences { ShortenReferences.Options.ALL_ENABLED }.process(elementsToShorten)

        runTemplateForInstanceParam(newDeclaration, nameSuggestions, editor)
        return newDeclaration
    }

    private fun hasTypeParameterReferences(containingClass: KtClassOrObject, element: KtNamedDeclaration): Boolean {
        val containingClassDescriptor = containingClass.unsafeResolveToDescriptor()
        return element.collectDescendantsOfType<KtTypeReference> {
            val referencedDescriptor = it.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, it]?.constructor?.declarationDescriptor
            referencedDescriptor is TypeParameterDescriptor && referencedDescriptor.containingDeclaration == containingClassDescriptor
        }.isNotEmpty()
    }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val project = element.project
        val containingClass = element.containingClassOrObject as KtClass
        if (element is KtClassOrObject) {
            val nameSuggestions =
                if (traverseOuterInstanceReferences(element, true)) getNameSuggestionsForOuterInstance(element) else emptyList()
            val outerInstanceName = nameSuggestions.firstOrNull()
            var movedClass: KtClassOrObject? = null
            val mover = object : Mover {
                override fun invoke(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration {
                    return Mover.Default(originalElement, targetContainer).apply { movedClass = this as KtClassOrObject }
                }
            }
            val moveDescriptor = MoveDeclarationsDescriptor(
                project,
                MoveSource(element),
                KotlinMoveTargetForCompanion(containingClass),
                MoveDeclarationsDelegate.NestedClass(null, outerInstanceName),
                moveCallback = MoveCallback { runTemplateForInstanceParam(movedClass!!, nameSuggestions, editor) }
            )
            MoveKotlinDeclarationsProcessor(moveDescriptor, mover).run()
            return
        }

        val (conflicts, externalUsages, outerInstanceUsages) = retrieveConflictsAndUsages(project, editor, element, containingClass)
            ?: return

        project.checkConflictsInteractively(conflicts) {
            fun performMove() {
                CommandProcessor.getInstance().executeCommand(project, {
                    ApplicationManagerEx.getApplicationEx().runWriteActionWithNonCancellableProgressInDispatchThread(
                        KotlinBundle.message("moving.to.companion.object"), project, null
                    ) {
                        doMove(it, element, externalUsages, outerInstanceUsages, editor)
                    }
                }, KotlinBundle.message("move.to.companion.object.command"), null)
            }

            if (isUnitTestMode()) {
                performMove()
            } else invokeLater {
                performMove()
            }
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction = MoveMemberToCompanionObjectIntention()

        fun removeModifiers(element: KtModifierListOwner) {
            element.removeModifier(KtTokens.OPEN_KEYWORD)
            element.removeModifier(KtTokens.FINAL_KEYWORD)
        }
    }
}