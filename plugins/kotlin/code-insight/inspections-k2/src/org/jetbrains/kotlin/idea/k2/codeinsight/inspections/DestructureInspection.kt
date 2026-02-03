// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.addRemoveModifier.setModifierList
import org.jetbrains.kotlin.psi.createDestructuringDeclarationByPattern
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver

internal class DestructureInspection : KotlinApplicableInspectionBase.Simple<KtDeclaration, UsagesToRemove>() {
    override fun getProblemDescription(
        element: KtDeclaration, context: UsagesToRemove
    ): @InspectionMessage String = KotlinBundle.message("use.destructuring.declaration")

    override fun createQuickFix(
        element: KtDeclaration, context: UsagesToRemove
    ): KotlinModCommandQuickFix<KtDeclaration> = UseDestructureDeclarationFix(context)

    override fun isApplicableByPsi(element: KtDeclaration): Boolean {
        return element.getUsageScopeElement() != null
    }

    override fun getApplicableRanges(element: KtDeclaration): List<TextRange> {
        val textRange = when (element) {
            is KtFunctionLiteral -> element.lBrace.textRange
            is KtNamedDeclaration -> element.nameIdentifier?.textRange
            else -> null
        }
        return listOfNotNull(textRange?.shiftLeft(element.textRange.startOffset))
    }

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            if (expression is KtFunctionLiteral) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

        override fun visitDeclaration(dcl: KtDeclaration) {
            visitTargetElement(dcl, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtDeclaration): UsagesToRemove? {
        return collectUsagesToRemove(element)
    }
}

internal class UseDestructureDeclarationFix(private val context: UsagesToRemove) : KotlinModCommandQuickFix<KtDeclaration>() {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("use.destructuring.declaration")

    override fun applyFix(
        project: Project, element: KtDeclaration, updater: ModPsiUpdater
    ) {
        val (usagesToRemove, removeSelectorInLoopRange) = context
        val psiFactory = KtPsiFactory(element.project)
        val parent = element.parent
        val (container, anchor) = if (parent is KtParameterList) parent.parent to null else parent to element
        val modifiableUsagesToRemove = usagesToRemove.map { usageData ->
            usageData.copy(
                usagesToReplace = usageData.usagesToReplace.map { updater.getWritable(it) }.toMutableList(),
                declarationToDrop = updater.getWritable(usageData.declarationToDrop)
            )
        }
        val nameValidator = KotlinDeclarationNameValidator(
            visibleDeclarationsContext = anchor ?: container as KtElement,
            checkVisibleDeclarationsContext = true,
            target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
            excludedDeclarations = modifiableUsagesToRemove.map {
                (it.declarationToDrop as? KtDestructuringDeclaration)?.entries ?: listOfNotNull(it.declarationToDrop)
            }.flatten()
        )
        val names = ArrayList<String>()
        val underscoreSupported =
            element.languageVersionSettings.supportsFeature(LanguageFeature.SingleUnderscoreForParameterName)
        val allUnused = modifiableUsagesToRemove.all { (_, usagesToReplace, variableToDrop) ->
            usagesToReplace.isEmpty() && variableToDrop == null
        }
        modifiableUsagesToRemove.forEach { (descriptor, usagesToReplace, variableToDrop, name) ->
            val suggestedName = if (usagesToReplace.isEmpty() && variableToDrop == null && underscoreSupported && !allUnused) {
                "_"
            } else {
                KotlinNameSuggester.suggestNameByName(name ?: descriptor) { nameValidator.validate(it) }
            }

            runWriteActionIfPhysical(element) {
                variableToDrop?.delete()
                usagesToReplace.forEach {
                    it.replace(psiFactory.createExpression(suggestedName))
                }
            }
            names.add(suggestedName)
        }
        val joinedNames = names.joinToString()
        when (element) {
            is KtParameter -> {
                val loopRange = (element.parent as? KtForExpression)?.loopRange
                runWriteActionIfPhysical(element) {
                    val type = element.typeReference?.let { ": ${it.text}" } ?: ""
                    element.replace(psiFactory.createDestructuringParameter("($joinedNames)$type"))
                    if (removeSelectorInLoopRange && loopRange is KtDotQualifiedExpression) {
                        loopRange.replace(loopRange.receiverExpression)
                    }
                }
            }

            is KtFunctionLiteral -> {
                val lambda = element.parent as KtLambdaExpression
                specifyExplicitLambdaSignature(
                    lambda, modifiableUsagesToRemove.joinToString(prefix = "(", postfix = ")") { it.componentName })
                runWriteActionIfPhysical(element) {
                    lambda.functionLiteral.valueParameters.singleOrNull()?.replace(
                        psiFactory.createDestructuringParameter("($joinedNames)")
                    )
                }
            }

            is KtVariableDeclaration -> {
                val rangeAfterEq = PsiChildRange(element.initializer, element.lastChild)
                val modifierList = element.modifierList?.copied<KtModifierList>()
                runWriteActionIfPhysical(element) {
                    val result = element.replace(
                        psiFactory.createDestructuringDeclarationByPattern(
                            "val ($joinedNames) = $0", rangeAfterEq
                        )
                    ) as KtModifierListOwner

                    if (modifierList != null) {
                        result.setModifierList(modifierList)
                    }
                }
            }
        }
    }
}

internal data class UsagesToRemove(val data: List<UsageData>, val removeSelectorInLoopRange: Boolean)

internal data class SingleUsageData(val callableName: String?, val usageToReplace: KtExpression?, val declarationToDrop: KtDeclaration?)

internal data class UsageData(
    val componentName: String,
    val usagesToReplace: MutableList<KtExpression> = mutableListOf(),
    var declarationToDrop: KtDeclaration? = null,
    var name: String? = null
) {
    /** Returns true if data is successfully added, false otherwise */
    fun add(newData: SingleUsageData, componentIndex: Int): Boolean {
        if (newData.declarationToDrop is KtDestructuringDeclaration) {
            val destructuringEntries = newData.declarationToDrop.entries
            if (componentIndex < destructuringEntries.size) {
                if (declarationToDrop != null) return false
                name = destructuringEntries[componentIndex].name ?: return false
                declarationToDrop = newData.declarationToDrop
            }
        } else {
            name = name ?: newData.declarationToDrop?.name
            declarationToDrop = declarationToDrop ?: newData.declarationToDrop
        }
        newData.usageToReplace?.let { usagesToReplace.add(it) }
        return true
    }
}

private fun KtDeclaration.getUsageScopeElement(): PsiElement? {
    val lambdaSupported = languageVersionSettings.supportsFeature(LanguageFeature.DestructuringLambdaParameters)
    return when (this) {
        is KtParameter -> {
            val parent = parent
            when {
                parent is KtForExpression -> parent
                parent.parent is KtFunctionLiteral -> if (lambdaSupported) parent.parent else null
                else -> null
            }
        }

        is KtProperty -> parent.takeIf { isLocal }
        is KtFunctionLiteral -> if (!hasParameterSpecification() && lambdaSupported) this else null
        else -> null
    }
}

context(_: KaSession)
private fun collectUsagesToRemove(declaration: KtDeclaration): UsagesToRemove? {
    val usageScopeElement = declaration.getUsageScopeElement() ?: return null

    val variableName = when (declaration) {
        is KtValVarKeywordOwner -> declaration.name
        is KtFunctionLiteral -> "it"
        else -> return null
    }

    val type = when (declaration) {
        is KtValVarKeywordOwner -> declaration.returnType
        is KtFunctionLiteral -> (declaration.expressionType as? KaFunctionType)?.parameterTypes?.singleOrNull()
        else -> return null
    }?.lowerBoundIfFlexible() as? KaClassType ?: return null

    if (type.isNullable) return null
    val classSymbol = type.expandedSymbol

    val (isMapEntry: Boolean, componentNames: List<String>) = if (classSymbol is KaNamedClassSymbol && classSymbol.isData) {
        val primaryCtor = classSymbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary } ?: return null
        false to primaryCtor.valueParameters.map { it.name.asString() }
    } else {
        val mapEntrySymbol = findClass(StandardClassIds.MapEntry) ?: return null
        if (classSymbol?.isSubClassOf(mapEntrySymbol) == true || mapEntrySymbol == classSymbol) {
            true to listOf("key", "value")
        } else {
            return null
        }
    }

    val usagesToRemove = componentNames.map { name -> UsageData(componentName = name) }.toMutableList()

    if (usageScopeElement.hasBadRefences(variableName, componentNames, isMapEntry, declaration, usagesToRemove)) return null

    val removeSelectorInLoopRange = if (isMapEntry) removeEntriesEntrySetInLoopRange(declaration) else false
    val droppedLastUnused = usagesToRemove.dropLastWhile { it.usagesToReplace.isEmpty() && it.declarationToDrop == null }
    return UsagesToRemove(droppedLastUnused.ifEmpty { usagesToRemove }, removeSelectorInLoopRange)
}

private fun PsiElement.hasBadRefences(
    variableName: @NlsSafe String?,
    componentNames: List<String>,
    isMapEntry: Boolean,
    declaration: KtDeclaration,
    usagesToRemove: MutableList<UsageData>,
): Boolean {
    val nameToIndex = buildMap {
        componentNames.forEachIndexed { index, name -> put(name, index) }
        if (isMapEntry) {
            put("getKey", 0)
            put("getValue", 1)
        }
    }

    return anyDescendantOfType<KtNameReferenceExpression> { ref ->
        if (ref.getReferencedName() != variableName) return@anyDescendantOfType false

        val sameDeclaration = ref.mainReference.resolve()?.let { it == declaration } ?: false
        if (!sameDeclaration) return@anyDescendantOfType false

        val applicable = getDataIfUsageIsApplicable(ref)
        if (applicable != null) {
            val callableName = applicable.callableName
            if (callableName == null) {
                for (idx in componentNames.indices) {
                    if (!usagesToRemove[idx].add(applicable, idx)) {
                        return@anyDescendantOfType true
                    }
                }
                return@anyDescendantOfType false
            }
            val idx = nameToIndex[callableName]
            if (idx != null) {
                return@anyDescendantOfType !usagesToRemove[idx].add(applicable, idx)
            }
        }

        true
    }
}

private fun removeEntriesEntrySetInLoopRange(
    declaration: KtDeclaration
): Boolean {
    val forLoop = declaration.parent as? KtForExpression
    val loopRange = forLoop?.loopRange
    val selectorExpression = (loopRange as? KtQualifiedExpression)?.selectorExpression as? KtNameReferenceExpression
    val selectorName = selectorExpression?.getReferencedName()
    if (selectorName == "entries" || selectorName == "entrySet") {
        analyze(selectorExpression) {
            val callableSymbol = selectorExpression.mainReference.resolveToSymbol() as? KaCallableSymbol
            if (callableSymbol != null) {
                val containingSymbol = callableSymbol.containingSymbol as? KaClassSymbol
                val mapEntrySymbol = findClass(StandardClassIds.Map)
                if (mapEntrySymbol != null && containingSymbol != null &&
                    (containingSymbol == mapEntrySymbol || containingSymbol.isSubClassOf(mapEntrySymbol))) {
                    return true
                }
            }
        }
    }
    return false
}

private fun getDataIfUsageIsApplicable(dataClassUsage: KtNameReferenceExpression): SingleUsageData? {
    val destructuringDecl = dataClassUsage.parent as? KtDestructuringDeclaration
    if (destructuringDecl != null && destructuringDecl.initializer == dataClassUsage) {
        return SingleUsageData(callableName = null, usageToReplace = null, declarationToDrop = destructuringDecl)
    }
    val qualifiedExpression = dataClassUsage.getQualifiedExpressionForReceiver() ?: return null
    val parent = qualifiedExpression.parent
    when (parent) {
        is KtBinaryExpression -> {
            if (parent.operationToken in KtTokens.ALL_ASSIGNMENTS && parent.left == qualifiedExpression) return null
        }

        is KtUnaryExpression -> {
            if (parent.operationToken == KtTokens.PLUSPLUS || parent.operationToken == KtTokens.MINUSMINUS) return null
        }
    }

    val property = parent as? KtProperty
    if (property != null && property.isVar) return null

    val selectorName = when (val selector = qualifiedExpression.selectorExpression) {
        is KtNameReferenceExpression -> selector.getReferencedName()
        else -> null
    }
    return SingleUsageData(callableName = selectorName, usageToReplace = qualifiedExpression, declarationToDrop = property)
}