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
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
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
    val refsToRename: Collection<PsiReference>,
    val kotlinCallsToReplace: Collection<KtCallElement>,
    val foreignRefsToRename: Collection<PsiReference>,
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
            val existingProperty = findExistingPropertyWithSameName(element, propertyName)
            return existingProperty == null
        }
}

private fun KtNamedFunction.getPropertyName(): String? {
    val functionName = this.name?.let { Name.identifier(it) } ?: return null
    return (propertyNameByGetMethodName(functionName) ?: functionName).toString()
}

private fun KaSession.prepareContext(element: KtNamedFunction): Context? {
    val propertyName = element.getPropertyName() ?: return null
    val newGetterName = JvmAbi.getterName(propertyName)

    val conflicts = mutableMapOf<PsiElement, ModShowConflicts.Conflict>()
    val elementsToChange = ElementsToChange()

    val functionSymbol = element.symbol
    val affectedCallablesWithSelf = getAffectedCallables(functionSymbol)

    for (callable in affectedCallablesWithSelf) {
        if (callable !is PsiNamedElement) continue

        addConflictIfCantRefactor(callable, conflicts)
        addConflictIfSamePropertyFound(callable, functionSymbol, conflicts, newGetterName, affectedCallablesWithSelf)

        val foundReferences = findReferencesToElement(callable) ?: continue
        for (reference in foundReferences) {
            checkReferenceCanBeChanged(reference, conflicts, elementsToChange)
        }
    }

    return Context(
        callables = affectedCallablesWithSelf,
        refsToRename = elementsToChange.kotlinRefsToRename,
        kotlinCallsToReplace = elementsToChange.kotlinCalls,
        foreignRefsToRename = elementsToChange.foreignRefs,
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

private fun findExistingPropertyWithSameName(element: KtNamedFunction, propertyName: String): KtProperty? {
    val scope = element.getContainingKtFile().declarations
        .filterIsInstance<KtProperty>()
        .filter { it.name == propertyName && it.receiverTypeReference?.text == element.receiverTypeReference?.text }

    return scope.firstOrNull()
}

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
    val (callables, refsToRename, kotlinCallsToReplace, foreignRefsToRename, newName, _, newGetterName) = writableElementContext

    val psiFactory = KtPsiFactory(element.project)
    val newReference = psiFactory.createExpression(newName)

    kotlinCallsToReplace.forEach { it.replace(newReference) }
    refsToRename.forEach { it.handleElementRename(newName) }
    foreignRefsToRename.forEach { it.handleElementRename(newGetterName) }

    val mainElement = findMainElement(element, callables, actionContext)
    callables.forEach {
        when (it) {
            is KtNamedFunction -> convertFunction(it, newName, psiFactory, updater, moveCaret = it == mainElement)
            is PsiMethod -> it.name = newGetterName
        }
    }
}

private fun convertFunction(
    originalFunction: KtNamedFunction,
    newName: String,
    psiFactory: KtPsiFactory,
    updater: ModPsiUpdater,
    moveCaret: Boolean
) {
    val propertyName = getPropertyName(originalFunction, newName)
    val newProperty = psiFactory.createDeclaration<KtProperty>(propertyName)
    val replaced = originalFunction.replaced(newProperty)
    if (moveCaret) updater.moveCaretTo(replaced.nameIdentifier!!.endOffset)
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
        modifier(originalFunctionText.take(funKeyword!!.getStartOffsetIn(originalFunction)))
        typeParams(originalFunction.typeParameters.map { it.text })
        originalFunction.receiverTypeReference?.let { receiver(it.text) }
        name(newName)
        originalFunction.getReturnTypeReference()?.let { returnType(it.text) }
        typeConstraints(originalFunction.typeConstraints.map { it.text })

        if (originalFunction.equalsToken != null) {
            getterExpression(originalFunction.bodyExpression!!.text, breakLine = originalFunction.typeReference != null)
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

private fun KaSession.addConflictIfSamePropertyFound(
    callable: PsiNamedElement,
    callableSymbol: KaCallableSymbol,
    conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>,
    getterName: String,
    callables: Collection<PsiElement>
) {
    if (callable is KtNamedFunction) {
        callable.containingKtFile
            .scopeContext(callable)
            .compositeScope()
            .callables { it == callableSymbol.name }
            .filterIsInstance<KaPropertySymbol>()
            .find {
                val receiverType = it.receiverType ?: return@find false
                (callableSymbol.containingSymbol as? KaClassifierSymbol)?.defaultType?.semanticallyEquals(receiverType)
                    ?: return@find false
            }?.let { reportDeclarationConflict(conflicts, declaration = it.psi!!) { s -> KotlinBundle.message("0.already.exists", s) } }
    } else if (callable is PsiMethod) {
        callable.checkDeclarationConflict(getterName, conflicts, callables)
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
    val (callables, refsToRename, kotlinCallsToReplace, foreignRefsToRename, newName, _, newGetterName) = elementContext
    return Context(
        callables = callables.map(updater::getWritable),
        refsToRename = refsToRename
            .map(PsiReference::getElement)
            .map(updater::getWritable)
            .mapNotNull(PsiElement::getReference),
        kotlinCallsToReplace = kotlinCallsToReplace.map(updater::getWritable),
        foreignRefsToRename = foreignRefsToRename
            .map(PsiReference::getElement)
            .map(updater::getWritable)
            .mapNotNull(PsiElement::getReference),
        newName = newName,
        conflicts = emptyMap(),
        newGetterName = newGetterName
    )
}
