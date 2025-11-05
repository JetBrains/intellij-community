// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.base.psi.getReturnTypeReference
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.isFunInterface
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CollectAffectedCallablesUtils.getAffectedCallables
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertFunctionToPropertyAndViceVersaUtils.addConflictIfCantRefactor
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertFunctionToPropertyAndViceVersaUtils.findReferencesToElement
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertFunctionToPropertyAndViceVersaUtils.reportDeclarationConflict
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.expressions.OperatorConventions.UNARY_OPERATION_NAMES

private data class Context(
    val callables: Collection<PsiElement>,
    val elementsToChange: ElementsToChange,
    val newName: String,
    val conflicts: Map<PsiElement, ModShowConflicts.Conflict>,
    val newGetterName: String
)

internal class ConvertFunctionToPropertyIntention :
    PsiBasedModCommandAction<KtNamedFunction>(/*element = */ null, /*elementClass =*/ KtNamedFunction::class.java) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.function.to.property")

    override fun getPresentation(context: ActionContext, element: KtNamedFunction): Presentation {
        return Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
    }

    override fun isElementApplicable(
        element: KtNamedFunction,
        context: ActionContext,
    ): Boolean = isApplicableByPsi(element, context) && isApplicableByAnalyze(element)

    override fun perform(
        context: ActionContext,
        element: KtNamedFunction
    ): ModCommand {
        val elementContext = analyze(element) {
            prepareContext(element)
        } ?: return ModCommand.nop()
        return ModCommand
            .showConflicts(elementContext.conflicts)
            .andThen(ModCommand.psiUpdate(element) { element, updater ->
                convertFunctionToProperty(
                    element,
                    elementContext,
                    updater,
                    context
                )
            })
    }

    private fun isApplicableByPsi(element: KtNamedFunction, context: ActionContext): Boolean {
        val funKeyword = element.funKeyword ?: return false
        val identifier = element.nameIdentifier ?: return false

        if (!TextRange(funKeyword.startOffset, identifier.endOffset).containsOffset(context.offset)) return false

        if (element.containingClass()?.isFunInterface() == true && !element.hasBody()) return false
        if (element.valueParameters.isNotEmpty() || element.isLocal) return false

        val name = element.name ?: return false
        return !(name == "invoke" || name == "iterator" || Name.identifier(name) in UNARY_OPERATION_NAMES.inverse().keys)
    }

    private fun isApplicableByAnalyze(element: KtNamedFunction): Boolean =
        analyze(element) {
            val functionSymbol = element.symbol
            val returnType = functionSymbol.returnType
            if (returnType.isUnitType || returnType.isNothingType) return false

            val propertyName = element.getPropertyName() ?: return false
            return findExistingPropertyWithSameName(element, propertyName) == null
        }

    private fun findExistingPropertyWithSameName(element: KtNamedFunction, propertyName: String): KtProperty? {
        val scope = element.getContainingKtFile().declarations
            .filterIsInstance<KtProperty>()
            .filter { it.name == propertyName && it.receiverTypeReference?.text == element.receiverTypeReference?.text }

        return scope.firstOrNull()
    }
}

private fun KtNamedFunction.getPropertyName(): String? {
    val functionName = this.nameIdentifier?.let { Name.identifier(it.text) } ?: return null
    return (propertyNameByGetMethodName(functionName) ?: functionName).toString()
}

private fun KaSession.prepareContext(element: KtNamedFunction): Context? {
    val propertyName = element.getPropertyName() ?: return null
    val newGetterName = JvmAbi.getterName(propertyName)

    val conflicts = mutableMapOf<PsiElement, ModShowConflicts.Conflict>()
    val elementsToChange = ElementsToChange()

    val functionSymbol = element.symbol
    val affectedCallablesWithSelf = getAffectedCallables(functionSymbol)

    val conflictCheckContext = ConflictCheckContext(
        conflicts = conflicts,
        getterName = newGetterName,
        callables = affectedCallablesWithSelf
    )

    for (callable in affectedCallablesWithSelf) {
        if (callable !is PsiNamedElement) continue

        addConflictIfCantRefactor(callable, conflicts)
        addConflictIfSamePropertyFound(callable, functionSymbol, conflictCheckContext)

        val foundReferences = findReferencesToElement(callable) ?: continue
        for (reference in foundReferences) {
            checkReferenceCanBeChanged(reference, conflicts, elementsToChange)
        }
    }

    return Context(
        callables = affectedCallablesWithSelf,
        elementsToChange = elementsToChange,
        newName = propertyName,
        conflicts = conflicts,
        newGetterName = newGetterName
    )
}

private class ElementsToChange {
    val kotlinCalls = mutableListOf<KtCallElement>()
    val kotlinRefsToRename = mutableListOf<PsiReference>()
    val foreignRefs = mutableListOf<PsiReference>()
}

private data class ConflictCheckContext(
    val conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>,
    val getterName: String,
    val callables: Collection<PsiElement>
)

private data class FunctionConversionContext(
    val newName: String,
    val psiFactory: KtPsiFactory,
    val updater: ModPsiUpdater
)

private fun checkReferenceCanBeChanged(
    reference: PsiReference,
    conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>,
    elementsToChange: ElementsToChange
) {
    if (reference is KtSimpleNameReference) {
        val expression = reference.expression
        val callElement = expression.getParentOfTypeAndBranch<KtCallElement> { calleeExpression }

        if (callElement != null && expression.getStrictParentOfType<KtCallableReferenceExpression>() == null) {
            checkTypeArgumentsNotEmpty(callElement, conflicts)
            if (checkValueArgumentsNotEmpty(callElement, conflicts)) return

            elementsToChange.kotlinCalls.add(callElement)
        } else {
            elementsToChange.kotlinRefsToRename.add(reference)
        }
    } else {
        // Java references will need to be renamed to getXxx
        elementsToChange.foreignRefs.add(reference)
    }
}

private fun convertFunctionToProperty(
    element: KtNamedFunction,
    elementContext: Context,
    updater: ModPsiUpdater,
    actionContext: ActionContext
) {
    val writableElementContext = getWritable(elementContext, updater)
    val (callables, elementsToChange, newName, _, newGetterName) = writableElementContext

    val psiFactory = KtPsiFactory(element.project)
    val newReference = psiFactory.createExpression(newName)

    elementsToChange.kotlinCalls.forEach { it.replace(newReference) }
    elementsToChange.kotlinRefsToRename.forEach { it.handleElementRename(newName) }
    elementsToChange.foreignRefs.forEach { it.handleElementRename(newGetterName) }

    val mainElement = findMainElement(element, callables, actionContext)
    callables.forEach {
        when (it) {
            is KtNamedFunction -> {
                val context = FunctionConversionContext(
                    newName = newName,
                    psiFactory = psiFactory,
                    updater = updater
                )
                convertFunction(it, context, moveCaret = it == mainElement)
            }

            is PsiMethod -> it.name = newGetterName
        }
    }
}

private fun convertFunction(
    originalFunction: KtNamedFunction,
    context: FunctionConversionContext,
    moveCaret: Boolean
) {
    val propertyName = getPropertyName(originalFunction, context.newName)
    val newProperty = context.psiFactory.createDeclaration<KtProperty>(propertyName)
    val replaced = originalFunction.replaced(newProperty)
    if (moveCaret) {
        replaced.nameIdentifier?.let { context.updater.moveCaretTo(it.endOffset) }
    }
}

private fun findMainElement(
    element: KtNamedFunction,
    callables: Collection<PsiElement>,
    actionContext: ActionContext
): PsiElement? {
    val offset = actionContext.offset
    return callables.find { element.containingFile == it.containingFile && offset in it.textRange }
}

private fun getPropertyName(
    originalFunction: KtNamedFunction,
    newName: String
): String {
    return KtPsiFactory.CallableBuilder(target = KtPsiFactory.CallableBuilder.Target.READ_ONLY_PROPERTY).apply {
        val originalFunctionText = originalFunction.text
        val funKeyword = originalFunction.funKeyword
        funKeyword?.let { keyword ->
            modifier(originalFunctionText.take(keyword.getStartOffsetIn(originalFunction)))
        }
        typeParams(originalFunction.typeParameters.map { it.text })
        originalFunction.receiverTypeReference?.let { receiver(it.text) }
        name(newName)
        originalFunction.getReturnTypeReference()?.let { returnType(it.text) }
        typeConstraints(originalFunction.typeConstraints.map { it.text })

        if (originalFunction.equalsToken != null) {
            originalFunction.bodyExpression?.let { bodyExpr ->
                getterExpression(bodyExpr.text, breakLine = originalFunction.typeReference != null)
            }
        } else {
            originalFunction.bodyBlockExpression?.let { body ->
                transform {
                    append("\nget() ")
                    append(body.text)
                }
            }
        }
    }.asString()
}

private fun checkTypeArgumentsNotEmpty(callElement: KtCallElement, conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>) {
    if (callElement.typeArguments.isNotEmpty()) {
        val message = KotlinBundle.message(
            "type.arguments.will.be.lost.after.conversion.0",
            StringUtil.htmlEmphasize(callElement.text)
        )
        conflicts[callElement] = ModShowConflicts.Conflict(listOf(message))
    }
}

private fun checkValueArgumentsNotEmpty(callElement: KtCallElement, conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>): Boolean {
    if (callElement.valueArguments.isNotEmpty()) {
        val message = KotlinBundle.message(
            "call.with.arguments.will.be.skipped.0",
            StringUtil.htmlEmphasize(callElement.text)
        )
        conflicts[callElement] = ModShowConflicts.Conflict(listOf(message))
        return true
    }
    return false
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.addConflictIfSamePropertyFound(
    affectedCallable: PsiNamedElement,
    initialElementFunctionSymbol: KaCallableSymbol,
    context: ConflictCheckContext
) {
    if (affectedCallable is KtNamedFunction) {
        val containingSymbolType = (initialElementFunctionSymbol.containingSymbol as? KaClassifierSymbol)?.defaultType
        val initialElementTypePointer = containingSymbolType?.createPointer() ?: return
        val initialElementSymbolName = initialElementFunctionSymbol.name
        affectedCallable.checkDeclarationConflict(initialElementTypePointer, initialElementSymbolName, context.conflicts)
    } else if (affectedCallable is PsiMethod) {
        affectedCallable.checkDeclarationConflict(context.getterName, context.conflicts, context.callables)
    }
}

@OptIn(KaExperimentalApi::class)
private fun KtNamedFunction.checkDeclarationConflict(
    initialElementTypePointer: KaTypePointer<KaType>,
    initialElementSymbolName: Name?,
    conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>
) {
    val affectedCallable = this
    // A separate `analyze` is needed to avoid KaBaseIllegalPsiException on analyzing the `affectedCallable` as `KtNamedFunction`
    analyze(affectedCallable) {
        val initialElementType = initialElementTypePointer.restore() ?: return@analyze
        affectedCallable.containingKtFile
            .scopeContext(affectedCallable)
            .compositeScope()
            .callables { it == initialElementSymbolName }
            .filterIsInstance<KaPropertySymbol>()
            .find {
                val receiverType = it.receiverType ?: return@find false
                initialElementType.semanticallyEquals(receiverType)
            }?.let { propertySymbol ->
                propertySymbol.psi?.let { psiElement ->
                    reportDeclarationConflict(conflicts, declaration = psiElement) { s ->
                        KotlinBundle.message(
                            "0.already.exists",
                            s
                        )
                    }
                }
            }
    }
}

// Almost the same function is kotlin.idea in org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringUtilKt#checkDeclarationConflict
// but can't port it because of circular dependencies
private fun PsiMethod.checkDeclarationConflict(
    name: String,
    conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>,
    callables: Collection<PsiElement>
) {
    containingClass
        ?.findMethodsByName(name, /*checkBases =*/ true)
        // as is necessary here: see KT-10386
        ?.firstOrNull { it.parameterList.parametersCount == 0 && !callables.contains(it.namedUnwrappedElement as PsiElement?) }
        ?.let { reportDeclarationConflict(conflicts, declaration = it) { s -> KotlinBundle.message("0.already.exists", s) } }
}

private fun getWritable(
    elementContext: Context,
    updater: ModPsiUpdater,
): Context {
    val (callables, elementsToChange, newName, _, newGetterName) = elementContext

    val writableElementsToChange = ElementsToChange().apply {
        kotlinCalls.addAll(elementsToChange.kotlinCalls.map(updater::getWritable))
        kotlinRefsToRename.addAll(
            elementsToChange.kotlinRefsToRename
                .map(PsiReference::getElement)
                .map(updater::getWritable)
                .mapNotNull(PsiElement::getReference)
        )
        foreignRefs.addAll(
            elementsToChange.foreignRefs
                .map(PsiReference::getElement)
                .map(updater::getWritable)
                .mapNotNull(PsiElement::getReference)
        )
    }

    return Context(
        callables = callables.map(updater::getWritable),
        elementsToChange = writableElementsToChange,
        newName = newName,
        conflicts = emptyMap(),
        newGetterName = newGetterName
    )
}
