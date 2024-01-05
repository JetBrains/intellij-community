// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.util.application
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.computeTypeInfoInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.core.getLambdaArgumentName
import org.jetbrains.kotlin.idea.intentions.ConvertToBlockBodyIntention
import org.jetbrains.kotlin.idea.refactoring.addTypeArgumentsIfNeeded
import org.jetbrains.kotlin.idea.refactoring.getQualifiedTypeArgumentList
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.*
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.psi.patternMatching.KotlinPsiUnifier
import org.jetbrains.kotlin.idea.util.psi.patternMatching.match
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.ObservableBindingTrace
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoAfter
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.checker.ClassicTypeSystemContext
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.createClassicTypeCheckerState
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

object K1IntroduceVariableHandler : KotlinIntroduceVariableHandler() {
    private class TypeCheckerImpl(private val project: Project) : KotlinTypeChecker by KotlinTypeChecker.DEFAULT {
        private inner class TypeSystemContextImpl : ClassicTypeSystemContext {
            override fun areEqualTypeConstructors(c1: TypeConstructorMarker, c2: TypeConstructorMarker): Boolean {
                require(c1 is TypeConstructor)
                require(c2 is TypeConstructor)
                return compareDescriptors(project, c1.declarationDescriptor, c2.declarationDescriptor)
            }
        }

        override fun equalTypes(a: KotlinType, b: KotlinType): Boolean = with(NewKotlinTypeChecker.Default) {
            val state = createClassicTypeCheckerState(isErrorTypeEqualsToAnything = false, typeSystemContext = TypeSystemContextImpl())

            state.equalTypes(a.unwrap(), b.unwrap())
        }
    }

    private class IntroduceVariableContext(
        project: Project,
        isVar: Boolean,
        nameSuggestions: List<Collection<String>>,
        replaceOccurrences: Boolean,
        isDestructuringDeclaration: Boolean,
        private val noTypeInference: Boolean,
        private val expressionType: KotlinType?,
        private val bindingContext: BindingContext,
        private val resolutionFacade: ResolutionFacade,
    ) : KotlinIntroduceVariableContext(project, isVar, nameSuggestions, replaceOccurrences, isDestructuringDeclaration) {
        override fun analyzeDeclarationAndSpecifyTypeIfNeeded(declaration: KtDeclaration) {
            if (declaration is KtProperty && declaration.typeReference == null && noTypeInference) {
                val typeToRender = expressionType ?: resolutionFacade.moduleDescriptor.builtIns.anyType

                declaration.typeReference = psiFactory.createType(IdeDescriptorRenderers.SOURCE_CODE.renderType(typeToRender))
                ShortenReferences.DEFAULT.process(declaration)
            }
        }

        override fun KtLambdaArgument.getLambdaArgumentNameByAnalyze(): Name? = getLambdaArgumentName(bindingContext)

        override fun convertToBlockBodyAndSpecifyReturnTypeByAnalyze(declaration: KtDeclarationWithBody): KtDeclarationWithBody =
            ConvertToBlockBodyIntention.Holder.convert(declaration)
    }

    fun KtExpression.findOccurrences(occurrenceContainer: PsiElement): List<KtExpression> =
        toRange().match(occurrenceContainer, KotlinPsiUnifier.DEFAULT).mapNotNull {
            val candidate = it.range.elements.first()

            if (candidate.isAssignmentLHS()) return@mapNotNull null

            when (candidate) {
                is KtExpression -> candidate
                is KtStringTemplateEntryWithExpression -> candidate.expression
                else -> throw KotlinExceptionWithAttachments("Unexpected candidate element ${candidate::class.java}")
                    .withPsiAttachment("candidate.kt", candidate)
            }
        }

    private fun KtExpression.shouldReplaceOccurrence(bindingContext: BindingContext, container: PsiElement?): Boolean {
        val effectiveParent = (parent as? KtScriptInitializer)?.parent ?: parent
        return container != effectiveParent || isUsedAsExpression(bindingContext)
    }

    private fun KtExpression.chooseApplicableComponentFunctionsForVariableDeclaration(
        haveOccurrencesToReplace: Boolean,
        editor: Editor?,
        callback: (List<FunctionDescriptor>) -> Unit
    ) {
        if (haveOccurrencesToReplace) return callback(emptyList())
        return chooseApplicableComponentFunctions(this, editor, callback = callback)
    }

    private fun executeMultiDeclarationTemplate(
        project: Project,
        editor: Editor,
        declaration: KtDestructuringDeclaration,
        suggestedNames: List<Collection<String>>,
        postProcess: (KtDeclaration) -> Unit
    ) {
        StartMarkAction.canStart(editor)?.let { return }

        val builder = TemplateBuilderImpl(declaration)
        for ((index, entry) in declaration.entries.withIndex()) {
            val templateExpression = object : Expression() {
                private val lookupItems = suggestedNames[index].map { LookupElementBuilder.create(it) }.toTypedArray()

                override fun calculateQuickResult(context: ExpressionContext?) = TextResult(suggestedNames[index].first())

                override fun calculateResult(context: ExpressionContext?) = calculateQuickResult(context)

                override fun calculateLookupItems(context: ExpressionContext?) = lookupItems
            }
            builder.replaceElement(entry, templateExpression)
        }

        val startMarkAction = StartMarkAction.start(editor, project, INTRODUCE_VARIABLE)
        editor.caretModel.moveToOffset(declaration.startOffset)

        project.executeWriteCommand(INTRODUCE_VARIABLE) {
            TemplateManager.getInstance(project).startTemplate(
                editor,
                builder.buildInlineTemplate(),
                object : TemplateEditingAdapter() {
                    private fun finishMarkAction() = FinishMarkAction.finish(project, editor, startMarkAction)

                    override fun templateFinished(template: Template, brokenOff: Boolean) {
                        if (!brokenOff) postProcess(declaration)

                        finishMarkAction()
                    }

                    override fun templateCancelled(template: Template?) = finishMarkAction()
                }
            )
        }
    }

    override fun doRefactoringWithSelectedTargetContainer(
        project: Project,
        editor: Editor?,
        expression: KtExpression,
        containers: Containers,
        isVar: Boolean,
        occurrencesToReplace: List<KtExpression>?,
        onNonInteractiveFinish: ((KtDeclaration) -> Unit)?
    ) {
        if (!isRefactoringApplicableByPsi(project, editor, expression)) return

        val substringInfo = expression.extractableSubstringInfo as? K1ExtractableSubstringInfo
        val physicalExpression = expression.substringContextOrThis

        val resolutionFacade = physicalExpression.getResolutionFacade()
        val bindingContext = resolutionFacade.analyze(physicalExpression, BodyResolveMode.FULL)

        val expressionType = substringInfo?.type ?: bindingContext.getType(physicalExpression) //can be null or error type
        val scope = physicalExpression.getResolutionScope(bindingContext, resolutionFacade)
        val dataFlowInfo = bindingContext.getDataFlowInfoAfter(physicalExpression)

        val bindingTrace = ObservableBindingTrace(BindingTraceContext())
        val typeInfoComputable = {
            physicalExpression.computeTypeInfoInContext(scope, physicalExpression, bindingTrace, dataFlowInfo).type
        }
        val typeNoExpectedType = substringInfo?.type
            ?: if (containers.targetContainer.isPhysical) {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    ThrowableComputable { runReadAction(typeInfoComputable) },
                    KotlinBundle.message("progress.title.calculating.type"),
                    true,
                    project
                )
            } else {
                // Preview mode
                typeInfoComputable()
            }
        val noTypeInference = expressionType != null
                && typeNoExpectedType != null
                && !TypeCheckerImpl(project).equalTypes(expressionType, typeNoExpectedType)

        if (expressionType == null && bindingContext.get(BindingContext.QUALIFIER, physicalExpression) != null) {
            return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.package.expression"))
        }

        if (expressionType != null && KotlinBuiltIns.isUnit(expressionType)) {
            return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.expression.has.unit.type"))
        }

        val typeArgumentList = getQualifiedTypeArgumentList(KtPsiUtil.safeDeparenthesize(physicalExpression))

        val isInplaceAvailable = isInplaceAvailable(editor, project)

        val allOccurrences = occurrencesToReplace ?: expression.findOccurrences(containers.occurrenceContainer)

        val callback = Pass.create { replaceChoice: OccurrencesChooser.ReplaceChoice ->
            val allReplaces = when (replaceChoice) {
                OccurrencesChooser.ReplaceChoice.ALL -> allOccurrences
                else -> listOf(expression)
            }

            val commonParent = if (allReplaces.isNotEmpty()) {
                PsiTreeUtil.findCommonParent(allReplaces.map { it.substringContextOrThis }) as KtElement
            } else {
                expression.parent as KtElement
            }
            var commonContainer = commonParent as? KtFile ?: commonParent.getContainer()!!
            if (commonContainer != containers.targetContainer && containers.targetContainer.isAncestor(commonContainer, true)) {
                commonContainer = containers.targetContainer
            }
            val replaceFirstOccurrence = allReplaces.firstOrNull()?.shouldReplaceOccurrence(bindingContext, commonContainer) == true

            fun postProcess(declaration: KtDeclaration) {
                if (typeArgumentList != null) {
                    val initializer = when (declaration) {
                        is KtProperty -> declaration.initializer
                        is KtDestructuringDeclaration -> declaration.initializer
                        else -> null
                    } ?: return
                    runWriteAction { addTypeArgumentsIfNeeded(initializer, typeArgumentList) }
                }

                if (editor != null && !replaceFirstOccurrence) {
                    editor.caretModel.moveToOffset(declaration.endOffset)
                }
            }

            physicalExpression.chooseApplicableComponentFunctionsForVariableDeclaration(
                haveOccurrencesToReplace = replaceFirstOccurrence || allReplaces.size > 1,
                editor,
            ) { componentFunctions ->
                val anchor = calculateAnchorForExpressions(commonParent, commonContainer, allReplaces)
                val validator = Fe10KotlinNewDeclarationNameValidator(
                    commonContainer,
                    anchor,
                    KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                )

                val isDestructuringDeclaration = componentFunctions.isNotEmpty()
                val suggestedNames = if (isDestructuringDeclaration) {
                    val collectingValidator = CollectingNameValidator(filter = validator)
                    componentFunctions.map { suggestNamesForComponent(it, project, collectingValidator) }
                } else {
                    Fe10KotlinNameSuggester.suggestNamesByExpressionAndType(
                        expression,
                        substringInfo?.type,
                        bindingContext,
                        validator,
                        "value"
                    ).let(::listOf)
                }

                val introduceVariableContext = IntroduceVariableContext(
                    project, isVar, suggestedNames, replaceFirstOccurrence, isDestructuringDeclaration,
                    noTypeInference, expressionType, bindingContext, resolutionFacade
                )

                if (!containers.targetContainer.isPhysical) {
                    // Preview mode
                    introduceVariableContext.convertToBlockIfNeededAndRunRefactoring(expression, commonContainer, commonParent, allReplaces)
                    return@chooseApplicableComponentFunctionsForVariableDeclaration
                }

                project.executeCommand(INTRODUCE_VARIABLE, null) {
                    application.runWriteAction {
                        introduceVariableContext.convertToBlockIfNeededAndRunRefactoring(
                            expression,
                            commonContainer,
                            commonParent,
                            allReplaces
                        )
                    }

                    val property = introduceVariableContext.introducedVariablePointer?.element ?: return@executeCommand

                    if (editor == null) {
                        onNonInteractiveFinish?.invoke(property)
                        return@executeCommand
                    }

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()

                    if (!isInplaceAvailable) {
                        postProcess(property)
                        return@executeCommand
                    }

                    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

                    when (property) {
                        is KtProperty -> {
                            KotlinVariableInplaceIntroducer(
                                property,
                                introduceVariableContext.reference?.element,
                                introduceVariableContext.references.mapNotNull { it.element }.toTypedArray(),
                                suggestedNames.single(),
                                isVar,
                                /*todo*/ false,
                                expressionType,
                                noTypeInference,
                                title = INTRODUCE_VARIABLE,
                                project,
                                editor,
                                ::postProcess
                            ).startInplaceIntroduceTemplate()
                        }

                        is KtDestructuringDeclaration -> {
                            executeMultiDeclarationTemplate(project, editor, property, suggestedNames, ::postProcess)
                        }

                        else -> throw AssertionError("Unexpected declaration: ${property.getElementTextWithContext()}")
                    }
                }
            }
        }

        if (isInplaceAvailable && occurrencesToReplace == null && !isUnitTestMode()) {
            val chooser = object : OccurrencesChooser<KtExpression>(editor) {
                override fun getOccurrenceRange(occurrence: KtExpression): TextRange? {
                    return occurrence.extractableSubstringInfo?.contentRange ?: occurrence.textRange
                }
            }
            application.invokeLater {
                chooser.showChooser(expression, allOccurrences, callback)
            }
        } else {
            callback.accept(OccurrencesChooser.ReplaceChoice.ALL)
        }
    }

    override fun filterContainersWithContainedLambdasByAnalyze(
        containersWithContainedLambdas: Sequence<ContainerWithContained>,
        physicalExpression: KtExpression,
        referencesFromExpressionToExtract: List<KtReferenceExpression>,
    ): Sequence<ContainerWithContained> {
        val file = physicalExpression.containingKtFile

        val resolutionFacade = physicalExpression.getResolutionFacade()
        val originalContext = resolutionFacade.analyze(physicalExpression, BodyResolveMode.FULL)

        return containersWithContainedLambdas.takeWhile { (_, neighbour) ->
            val scope = neighbour.getResolutionScope(originalContext, resolutionFacade)
            val newContext = physicalExpression.analyzeInContext(scope, neighbour)
            val project = file.project
            referencesFromExpressionToExtract.all {
                val originalDescriptor = originalContext[BindingContext.REFERENCE_TARGET, it]
                if (originalDescriptor is ValueParameterDescriptor && (originalContext[BindingContext.AUTO_CREATED_IT, originalDescriptor] == true)) {
                    return@all originalDescriptor.containingDeclaration.source.getPsi().isAncestor(neighbour, true)
                }

                val newDescriptor = newContext[BindingContext.REFERENCE_TARGET, it]
                compareDescriptors(project, newDescriptor, originalDescriptor)
            }
        }
    }
}
