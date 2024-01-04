// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
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
import org.jetbrains.kotlin.idea.core.moveInsideParenthesesAndReplaceWith
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
import org.jetbrains.kotlin.lexer.KtTokens
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
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.sure
import kotlin.math.min

object K1IntroduceVariableHandler : KotlinIntroduceVariableHandler() {
    private val EXPRESSION_KEY = Key.create<Boolean>("EXPRESSION_KEY")
    private val REPLACE_KEY = Key.create<Boolean>("REPLACE_KEY")
    private val COMMON_PARENT_KEY = Key.create<Boolean>("COMMON_PARENT_KEY")

    private var KtExpression.isOccurrence: Boolean by NotNullablePsiCopyableUserDataProperty(Key.create("OCCURRENCE"), false)

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
        private val nameSuggestions: List<Collection<String>>,
        private val allReplaces: List<KtExpression>,
        private val replaceFirstOccurrence: Boolean,
        private val noTypeInference: Boolean,
        private val expressionType: KotlinType?,
        private val isDestructuringDeclaration: Boolean,
        private val bindingContext: BindingContext,
        private val resolutionFacade: ResolutionFacade,
    ) {
        private val psiFactory = KtPsiFactory(project)

        var introducedVariablePointer: SmartPsiElementPointer<KtDeclaration>? = null
        var reference: SmartPsiElementPointer<KtExpression>? = null
        val references = ArrayList<SmartPsiElementPointer<KtExpression>>()

        private fun replaceExpression(
            actualExpression: KtExpression,
            expressionToReplace: KtExpression,
            addToReferences: Boolean
        ): KtExpression {
            val isActualExpression = actualExpression == expressionToReplace

            val replacement = psiFactory.createExpression(nameSuggestions.single().first())
            val substringInfo = expressionToReplace.extractableSubstringInfo
            var result = when {
                expressionToReplace.isLambdaOutsideParentheses() -> {
                    val functionLiteralArgument = expressionToReplace.getStrictParentOfType<KtLambdaArgument>()!!
                    val newCallExpression = functionLiteralArgument.moveInsideParenthesesAndReplaceWith(replacement, bindingContext)
                    newCallExpression.valueArguments.last().getArgumentExpression()!!
                }
                substringInfo != null -> substringInfo.replaceWith(replacement)
                else -> expressionToReplace.replace(replacement) as KtExpression
            }

            result = result.removeTemplateEntryBracesIfPossible()

            if (addToReferences) {
                references.addIfNotNull(SmartPointerManager.createPointer(result))
            }

            if (isActualExpression) {
                reference = SmartPointerManager.createPointer(result)
            }

            return result
        }

        private fun createBasicPropertyOrDestructuringDeclaration(expression: KtExpression, isVar: Boolean): KtDeclaration {
            val initializer = (expression as? KtParenthesizedExpression)?.expression ?: expression
            val initializerText = if (initializer.mustBeParenthesizedInInitializerPosition()) "(${initializer.text})" else initializer.text

            val varOvVal = if (isVar) "var" else "val"

            return if (isDestructuringDeclaration) {
                buildString {
                    nameSuggestions.joinTo(this, prefix = "$varOvVal (", postfix = ")") { it.first() }
                    append(" = ")
                    append(initializerText)
                }.let { psiFactory.createDestructuringDeclaration(it) }
            } else {
                buildString {
                    append("$varOvVal ")
                    append(nameSuggestions.single().first())
                    if (noTypeInference) {
                        val typeToRender = expressionType ?: resolutionFacade.moduleDescriptor.builtIns.anyType
                        append(": ").append(IdeDescriptorRenderers.SOURCE_CODE.renderType(typeToRender))
                    }
                    append(" = ")
                    append(initializerText)
                }.let { psiFactory.createProperty(it) }
            }
        }

        private fun runRefactoring(
            isVar: Boolean,
            expression: KtExpression,
            commonContainer: PsiElement,
            commonParent: PsiElement,
            allReplaces: List<KtExpression>
        ) {
            var property = createBasicPropertyOrDestructuringDeclaration(expression, isVar)
            var anchor = calculateAnchor(commonParent, commonContainer, allReplaces) ?: return
            val needBraces = commonContainer !is KtBlockExpression && commonContainer !is KtClassBody && commonContainer !is KtFile
            if (!needBraces) {
                property = commonContainer.addBefore(property, anchor) as KtDeclaration
                commonContainer.addBefore(psiFactory.createNewLine(), anchor)
            } else {
                var emptyBody: KtExpression = psiFactory.createEmptyBody()
                val firstChild = emptyBody.firstChild
                emptyBody.addAfter(psiFactory.createNewLine(), firstChild)


                val haveOccurrencesToReplace = replaceFirstOccurrence || allReplaces.size > 1
                if (haveOccurrencesToReplace) {
                    for ((index, replace) in allReplaces.withIndex()) {
                        if (index == 0 && !replaceFirstOccurrence) continue

                        val exprAfterReplace = replaceExpression(expression, replace, false)
                        exprAfterReplace.isOccurrence = true
                        if (anchor == replace) {
                            anchor = exprAfterReplace
                        }
                    }

                    var oldElement: PsiElement = commonContainer
                    if (commonContainer is KtWhenEntry) {
                        val body = commonContainer.expression
                        if (body != null) {
                            oldElement = body
                        }
                    } else if (commonContainer is KtContainerNode) {
                        val children = commonContainer.children
                        for (child in children) {
                            if (child is KtExpression) {
                                oldElement = child
                            }
                        }
                    }
                    //ugly logic to make sure we are working with right actual expression
                    var actualExpression = reference?.element ?: return
                    var diff = actualExpression.textRange.startOffset - oldElement.textRange.startOffset
                    var actualExpressionText = actualExpression.text
                    val newElement = emptyBody.addAfter(oldElement, firstChild)
                    var elem: PsiElement? = findElementByOffsetAndText(diff, actualExpressionText, newElement)
                    if (elem != null) {
                        reference = SmartPointerManager.createPointer(elem as KtExpression)
                    }
                    emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                    property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                    emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                    actualExpression = reference?.element ?: return
                    diff = actualExpression.textRange.startOffset - emptyBody.textRange.startOffset
                    actualExpressionText = actualExpression.text
                    emptyBody = anchor.replace(emptyBody) as KtBlockExpression
                    elem = findElementByOffsetAndText(diff, actualExpressionText, emptyBody)
                    if (elem != null) {
                        reference = SmartPointerManager.createPointer(elem as KtExpression)
                    }

                    emptyBody.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                                if (!expression.isOccurrence) return

                                expression.isOccurrence = false
                                references.add(SmartPointerManager.createPointer(expression))
                            }
                        })
                } else {
                    val parent = anchor.parent
                    val copyTo = parent.lastChild
                    val copyFrom = anchor.nextSibling

                    property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                    emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                    if (copyFrom != null && copyTo != null) {
                        emptyBody.addRangeAfter(copyFrom, copyTo, property)
                        parent.deleteChildRange(copyFrom, copyTo)
                    }
                    emptyBody = anchor.replace(emptyBody) as KtBlockExpression
                }
                for (child in emptyBody.children) {
                    if (child is KtProperty) {
                        property = child
                    }
                }
                if (commonContainer is KtContainerNode) {
                    if (commonContainer.parent is KtIfExpression) {
                        val next = commonContainer.nextSibling
                        if (next != null) {
                            val nextnext = next.nextSibling
                            if (nextnext != null && nextnext.node.elementType == KtTokens.ELSE_KEYWORD) {
                                if (next is PsiWhiteSpace) {
                                    next.replace(psiFactory.createWhiteSpace())
                                }
                            }
                        }
                    }
                }
            }
            if (!needBraces) {
                for ((index, replace) in allReplaces.withIndex()) {
                    if (index == 0 && !replaceFirstOccurrence) {
                        val sibling = PsiTreeUtil.skipSiblingsBackward(replace, PsiWhiteSpace::class.java)
                        if (sibling == property) {
                            replace.parent.deleteChildRange(property.nextSibling, replace)
                        } else {
                            replace.delete()
                        }
                    } else {
                        replaceExpression(expression, replace, true)
                    }
                }
            }
            introducedVariablePointer = property.createSmartPointer()
            if (noTypeInference) {
                ShortenReferences.DEFAULT.process(property)
            }
        }

        fun runRefactoring(isVar: Boolean, expression: KtExpression, commonContainer: PsiElement, commonParent: PsiElement) {
            if (commonContainer !is KtDeclarationWithBody) return runRefactoring(
                isVar,
                expression,
                commonContainer,
                commonParent,
                allReplaces
            )

            commonContainer.bodyExpression.sure { "Original body is not found: $commonContainer" }

            expression.substringContextOrThis.putCopyableUserData(EXPRESSION_KEY, true)
            for (replace in allReplaces) {
                replace.substringContextOrThis.putCopyableUserData(REPLACE_KEY, true)
            }
            commonParent.putCopyableUserData(COMMON_PARENT_KEY, true)

            val newDeclaration = ConvertToBlockBodyIntention.Holder.convert(commonContainer)

            val newCommonContainer = newDeclaration.bodyBlockExpression.sure { "New body is not found: $newDeclaration" }

            val newExpression = newCommonContainer.findExpressionByCopyableDataAndClearIt(EXPRESSION_KEY)
                ?.getSubstringExpressionOrThis(expression)
            val newCommonParent = newCommonContainer.findElementByCopyableDataAndClearIt(COMMON_PARENT_KEY)
            val newAllReplaces = (allReplaces zip newCommonContainer.findExpressionsByCopyableDataAndClearIt(REPLACE_KEY)).map {
                val (originalReplace, newReplace) = it
                newReplace.getSubstringExpressionOrThis(originalReplace)
            }

            runRefactoring(
                isVar,
                newExpression ?: return,
                newCommonContainer,
                newCommonParent ?: return,
                newAllReplaces
            )
        }

        private fun KtExpression.getSubstringExpressionOrThis(oldExpression: KtExpression): KtExpression {
            val oldSubstringInfo = oldExpression.extractableSubstringInfo ?: return this
            val newSubstringInfo = oldSubstringInfo.copy(this as KtStringTemplateExpression)
            return newSubstringInfo.createExpression()
        }
    }

    private fun calculateAnchor(commonParent: PsiElement, commonContainer: PsiElement, allReplaces: List<KtExpression>): PsiElement? {
        if (commonParent != commonContainer) return commonParent.parentsWithSelf.firstOrNull { it.parent == commonContainer }
        val startOffset = allReplaces.fold(commonContainer.endOffset) { offset, expr ->
            min(offset, expr.substringContextOrThis.startOffset)
        }

        return commonContainer.allChildren.lastOrNull { it.textRange.contains(startOffset) }
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
        return isUsedAsExpression(bindingContext) || container != effectiveParent
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
                    ThrowableComputable { runReadAction(typeInfoComputable) }, KotlinBundle.message("progress.title.calculating.type"), true, project)
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
                val validator = Fe10KotlinNewDeclarationNameValidator(
                    commonContainer,
                    calculateAnchor(commonParent, commonContainer, allReplaces),
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
                    project, suggestedNames, allReplaces,
                    replaceFirstOccurrence, noTypeInference, expressionType, isDestructuringDeclaration, bindingContext, resolutionFacade
                )

                if (!containers.targetContainer.isPhysical) {
                    // Preview mode
                    introduceVariableContext.runRefactoring(isVar, expression, commonContainer, commonParent)
                    return@chooseApplicableComponentFunctionsForVariableDeclaration
                }

                project.executeCommand(INTRODUCE_VARIABLE, null) {
                    runWriteAction { introduceVariableContext.runRefactoring(isVar, expression, commonContainer, commonParent) }

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
            invokeLater {
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
