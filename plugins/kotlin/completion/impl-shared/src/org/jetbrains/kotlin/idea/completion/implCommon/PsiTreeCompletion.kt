// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.completion.isPositionInsideImportOrPackageDirective
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.inferClassIdByPsi
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isDotSelector
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.util.*
import javax.swing.Icon

class PsiTreeCompletion {
    private val methodIcon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Method)

    /**
     * We currently model four types of scopes.
     * In a local scope, declarations can only be used after the declaration.
     * In a class scope, declarations are also recorded as being available with a receiver (e.g. obj.property).
     * If the scope is that of a companion object, the declarations are also recorded in its containing class scope.
     * The global scope is the global scope inside a Kotlin file, and declarations here can be used before their declaration,
     * but they are not recorded as also being available when using a receiver.
     */
    private enum class ScopeType {
        LOCAL, GLOBAL, CLASS, COMPANION_OBJECT
    }

    /**
     * A basic structure modeling a scope in the Kotlin file.
     * Each scope records where declarations are defined within it and can contain multiple sub-scopes.
     * See [ScopeType] for the different types of scopes modeled by this class.
     */
    private class LookupScope(
        val parentScope: LookupScope? = null,
        val textRange: TextRange,
        val scopeType: ScopeType
    ) {
        private class LookupDataEntry(val offset: Int, val data: LookupData)

        private val scopeData = mutableListOf<LookupDataEntry>()
        private val subScopes = TreeMap<Int, LookupScope>()

        fun findGlobalScope(): LookupScope {
            if (parentScope == null) return this
            return parentScope.globalScope
        }

        val globalScope by lazy { findGlobalScope() }

        fun registerSubScope(range: TextRange, scopeType: ScopeType): LookupScope {
            val subScope = LookupScope(parentScope = this, range, scopeType)
            subScopes[range.startOffset] = subScope
            return subScope
        }

        private fun collectAllData(set: MutableSet<LookupData>) {
            set.addAll(scopeData.map { it.data })
            subScopes.forEach { it.value.collectAllData(set) }
        }

        /**
         * Returns the data within the scope and contained in any sub-scope
         */
        fun allData(): Set<LookupData> = mutableSetOf<LookupData>().apply(this::collectAllData)

        fun recordData(offset: Int, data: LookupData) {
            val newEntry = LookupDataEntry(offset, data)
            scopeData.add(newEntry)
            if (scopeType == ScopeType.COMPANION_OBJECT) {
                parentScope?.scopeData?.add(newEntry)
            }
            if (!data.requiresReceiver && scopeType == ScopeType.CLASS) {
                val copiedData = data.copy(requiresReceiver = true)
                // Record it both in the local scope (so it is closer), and the global scope (so it can be used anywhere)
                recordData(offset, copiedData)
                globalScope.recordData(offset = 0, copiedData)
            }
        }

        class PrioritizedLookupData(val lookupData: LookupData, val distance: Int)

        fun findLookupData(offset: Int): List<PrioritizedLookupData> {
            val result = mutableListOf<PrioritizedLookupData>()
            findLookupData(offset, distance = 0, result)
            return result
        }

        fun findScopeForOffset(offset: Int): LookupScope {
            val firstScopeBefore = subScopes.floorEntry(offset) ?: return this

            if (firstScopeBefore.value.textRange.contains(offset)) {
                return firstScopeBefore.value.findScopeForOffset(offset)
            }
            return this
        }

        private fun findLookupData(offset: Int, distance: Int, result: MutableList<PrioritizedLookupData>) {
            parentScope?.findLookupData(offset, distance + 1, result)
            for (entry in scopeData) {
                if (scopeType == ScopeType.LOCAL && entry.offset > offset) continue
                result.add(PrioritizedLookupData(entry.data, distance))
            }
        }
    }

    /**
     * Basic data used to define elements that can be looked up in the completion.
     */
    private data class LookupData(
        val name: String,
        val isFunction: Boolean,
        val parameters: List<ParameterData> = emptyList(),
        val icon: Icon,
        val requiresReceiver: Boolean = false,
        val type: String? = null
    ) {
        fun toLookupElement(): LookupElementBuilder {
            val parameterNamesStr = parameters.joinToString()
            var builder = LookupElementBuilder.create(name)
                .withInsertHandler(PsiTreeCompletionInsertionHandler(name, isFunction, parameters))
                .withTypeText(type)
                .withIcon(icon)

            if (isFunction) {
                builder = builder.appendTailText("(", true)
                    .appendTailText(parameterNamesStr, true)
                    .appendTailText(")", true)
            }

            return builder
        }
    }

    private fun KtDeclaration.toIcon(): Icon = when (this) {
        is KtParameter -> KotlinIcons.PARAMETER
        is KtVariableDeclaration -> if (isVar) KotlinIcons.VAR else KotlinIcons.VAL
        is KtFunction -> if (parentOfType<KtDeclaration>() is KtClassOrObject) methodIcon else KotlinIcons.FUNCTION
        is KtEnumEntry -> KotlinIcons.ENUM
        is KtClass -> if (isInterface()) KotlinIcons.INTERFACE else if (isAbstract()) KotlinIcons.ABSTRACT_CLASS else KotlinIcons.CLASS
        is KtObjectDeclaration -> KotlinIcons.OBJECT
        else -> AllIcons.Nodes.Unknown
    }

    private fun KtExpression.getTypeIfPossible(): String? = when (this) {
        is KtStringTemplateExpression -> "String"
        is KtConstantExpression -> inferClassIdByPsi()?.shortClassName?.asString()
        else -> null
    }

    private fun KtDeclaration.getTypeIfPossible(): String? = when {
        this is KtCallableDeclaration && typeReference != null -> typeReference?.text
        this is KtDeclarationWithInitializer -> initializer?.getTypeIfPossible()
        else -> null
    }

    private fun KtDeclaration.toLookupData(): LookupData? {
        if (this is KtConstructor<*>) return null
        val name = name ?: return null
        val icon = toIcon()
        val isFunction = this is KtFunction
        val parameters = (this as? KtFunction)?.valueParameters?.mapNotNull {
            val paramName = it.name ?: return@mapNotNull null
            val typeName = it.typeReference?.text ?: return@mapNotNull null
            ParameterData(paramName, typeName)
        } ?: emptyList()
        return LookupData(
            name = name,
            isFunction = isFunction,
            parameters = parameters,
            icon = icon,
            requiresReceiver = (this as? KtCallableDeclaration)?.receiverTypeReference != null,
            type = getTypeIfPossible()
        )
    }

    private fun String.stdLibFunction() = LookupData(name = this, isFunction = true, icon = KotlinIcons.FUNCTION)
    private fun String.stdLibClass() = LookupData(name = this, isFunction = false, icon = KotlinIcons.CLASS)
    private fun String.stdLibObject() = LookupData(name = this, isFunction = false, icon = KotlinIcons.OBJECT)


    // Basic types and definitions from the stdlib we always want to include in the results
    private val stdLibClasses = listOf("Int", "Short", "Byte", "Char", "Boolean", "String", "Double", "Float", "Any", "Nothing")
        .map { it.stdLibClass() }
    private val stdLibObjects = listOf("Unit").map { it.stdLibObject() }
    private val stdlibFunctions = listOf("println", "print", "readLine", "readln").map { it.stdLibFunction() }
    private val stdlibLookupData = stdLibClasses + stdlibFunctions + stdLibObjects

    private val scopeVisitor = object : KtTreeVisitor<LookupScope>() {
        private fun LookupScope.recordDeclaration(dcl: KtDeclaration) {
            val data = dcl.toLookupData() ?: return
            recordData(dcl.startOffset, data)
        }

        override fun visitDeclaration(dcl: KtDeclaration, data: LookupScope): Void? {
            if (dcl !is KtNamedFunction && dcl !is KtClassOrObject && dcl !is KtSecondaryConstructor) {
                // We handle these declarations separately because they need to open a new scope for the
                // function parameters, so they do not leak to the outside scope.
                data.recordDeclaration(dcl)
            }
            return super.visitDeclaration(dcl, data)
        }

        override fun visitClassOrObject(classOrObject: KtClassOrObject, data: LookupScope): Void? {
            data.recordDeclaration(classOrObject)
            val scopeType = if ((classOrObject as? KtObjectDeclaration)?.isCompanion() == true) {
                ScopeType.COMPANION_OBJECT
            } else {
                ScopeType.CLASS
            }
            return super.visitClassOrObject(classOrObject, data.registerSubScope(classOrObject.textRange, scopeType))
        }

        override fun visitForExpression(expression: KtForExpression, data: LookupScope): Void? {
            return super.visitForExpression(expression, data.registerSubScope(expression.textRange, ScopeType.LOCAL))
        }

        override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, data: LookupScope): Void? {
            return super.visitSecondaryConstructor(constructor, data.registerSubScope(constructor.textRange, ScopeType.LOCAL))
        }

        override fun visitNamedFunction(function: KtNamedFunction, data: LookupScope): Void? {
            data.recordDeclaration(function)
            return super.visitNamedFunction(function, data.registerSubScope(function.textRange, ScopeType.LOCAL))
        }

        override fun visitBlockExpression(expression: KtBlockExpression, data: LookupScope): Void? {
            return super.visitBlockExpression(expression, data.registerSubScope(expression.textRange, ScopeType.LOCAL))
        }
    }

    private class GlobalUsagesVisitorData(val globalScope: LookupScope, val allData: MutableMap<String, MutableList<LookupData>>)

    private val detectGlobalUsagesVisitor = object : KtTreeVisitor<GlobalUsagesVisitorData>() {
        private fun addGlobalCallIfNecessary(
            expression: KtExpression,
            name: String,
            globalData: GlobalUsagesVisitorData
        ) {
            val existingEntries = globalData.allData.getOrPut(name) { mutableListOf() }
            val expressionToAnalyze = (expression.parent as? KtCallExpression) ?: expression
            val isFunction = expressionToAnalyze is KtCallExpression
            val isClass = expression.parent is KtUserType
            val icon = if (isFunction) KotlinIcons.FUNCTION else if (isClass) KotlinIcons.CLASS else KotlinIcons.VAL

            val requiresReceiver = expressionToAnalyze.isDotSelector()
            if (existingEntries.any { it.requiresReceiver == requiresReceiver }) return

            val data = LookupData(name, isFunction, emptyList(), icon, requiresReceiver)
            globalData.globalScope.recordData(offset = 0, data)
            existingEntries.add(data)
        }

        private fun isOperator(expression: KtSimpleNameExpression): Boolean {
            val operation = expression.parent as? KtOperationExpression ?: return false
            return operation.operationReference == expression
        }

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: GlobalUsagesVisitorData): Void? {
            // Skip keywords, operators, and anything inside package/import declarations
            if (!isPositionInsideImportOrPackageDirective(expression) &&
                expression.parent !is KtInstanceExpressionWithLabel &&
                !isOperator(expression)
            ) {
                addGlobalCallIfNecessary(expression, expression.text, data)
            }

            return super.visitSimpleNameExpression(expression, data)
        }
    }

    private fun shouldSkipCompletion(position: PsiElement): Boolean{
        if (position.parent is KtOperationReferenceExpression) return true
        if (position.parent is KtLiteralStringTemplateEntry) return true
        if (KtPsiUtil.isInComment(position)) return true
        return false
    }

    fun complete(position: PsiElement, prefixMatcher: PrefixMatcher, consumer: (LookupElement) -> Unit) {
        if (shouldSkipCompletion(position)) return
        val containingFile = position.containingFile as? KtFile ?: return
        val canUseReceiver = (position.parent as? KtSimpleNameExpression)?.getReceiverExpression() != null

        // Step 1: parse through all the scopes and establish which declarations are defined where
        val globalScope = LookupScope(parentScope = null, containingFile.textRange, ScopeType.GLOBAL)
        // also add predefined stdlib functions/classes
        for (data in stdlibLookupData) {
            globalScope.recordData(offset = 0, data)
        }
        containingFile.accept(scopeVisitor, globalScope)
        val allData = globalScope.allData().groupBy { it.name }.mapValues { it.value.toMutableList() }.toMutableMap()
        // Step 2: find all function calls and property access that are used somewhere in the file.
        // If they are not found as local declarations, they might be used from imported files, so add them as well.
        val globalData = GlobalUsagesVisitorData(globalScope, allData)
        containingFile.accept(detectGlobalUsagesVisitor, globalData)

        // Step 3: Find the scope at the current position and filter the results by the prefix
        val matchingLookupDataAtOffset = globalScope.findScopeForOffset(position.startOffset)
            .findLookupData(position.startOffset)
            .filter { prefixMatcher.prefixMatches(it.lookupData.name) && canUseReceiver == it.lookupData.requiresReceiver }
            .sortedBy { it.distance } // Sort so that duplicate elements are ordered correctly, only the first one is shown

        for (prioritizedLookupData in matchingLookupDataAtOffset) {
            val lookupElementBuilder = prioritizedLookupData.lookupData.toLookupElement()
            // This provides some rudimentary sorting based on scope distance, but it is mostly overruled by ML sorting anyway
            val priority = -prioritizedLookupData.distance.toDouble()
            consumer(PrioritizedLookupElement.withPriority(lookupElementBuilder, priority))
        }
    }
}

@Serializable
internal class PsiTreeCompletionInsertionHandler(
    val name: String,
    val isFunction: Boolean,
    val parameters: List<ParameterData>,
) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (!isFunction) return
        val endOffset = context.startOffset + name.length
        context.document.insertString(endOffset, "()")
        if (parameters.isNotEmpty()) {
            context.editor.moveCaret(endOffset + 1)
        } else {
            context.editor.moveCaret(endOffset + 2)
        }
    }
}

@Serializable
internal class ParameterData(val name: String, val type: String) {
    override fun toString(): String = "$name: $type"
}