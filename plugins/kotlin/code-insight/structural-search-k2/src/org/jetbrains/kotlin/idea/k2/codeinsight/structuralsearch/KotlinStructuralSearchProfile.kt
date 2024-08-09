// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.structuralsearch.*
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder
import com.intellij.structuralsearch.plugin.replace.impl.Replacer
import com.intellij.structuralsearch.plugin.ui.Configuration
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.filters.*
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinAlsoMatchCompanionObjectPredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinAlsoMatchValVarPredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinExprTypePredicate
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.predicates.KotlinMatchCallSemantics
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor.KotlinCompilingVisitor
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor.KotlinMatchingVisitor
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor.KotlinRecursiveElementVisitor
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor.KotlinRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import kotlin.math.min

class KotlinStructuralSearchProfile : StructuralSearchProfile() {
    override fun isMatchNode(element: PsiElement?): Boolean = element !is PsiWhiteSpace

    override fun createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): KotlinMatchingVisitor =
        KotlinMatchingVisitor(globalVisitor)

    override fun createCompiledPattern(): CompiledPattern = object : CompiledPattern() {
        init {
            strategy = KotlinMatchingStrategy
        }

        override fun getTypedVarPrefixes(): Array<String> = arrayOf(TYPED_VAR_PREFIX)

        override fun isTypedVar(str: String): Boolean = when {
            str.isEmpty() -> false
            str[0] == '@' -> str.regionMatches(1, TYPED_VAR_PREFIX, 0, TYPED_VAR_PREFIX.length)
            else -> str.startsWith(TYPED_VAR_PREFIX)
        }

        override fun getTypedVarString(element: PsiElement): String {
            val typedVarString = super.getTypedVarString(element)
            return if (typedVarString.firstOrNull() == '@') typedVarString.drop(1) else typedVarString
        }
    }

    override fun isMyLanguage(language: Language): Boolean = language == KotlinLanguage.INSTANCE

    override fun getTemplateContextTypeClass(): Class<KotlinTemplateContextType.Generic> = KotlinTemplateContextType.Generic::class.java

    override fun getPredefinedTemplates(): Array<Configuration> = KotlinPredefinedConfigurations.createPredefinedTemplates()

    override fun getDefaultFileType(fileType: LanguageFileType?): LanguageFileType = fileType ?: KotlinFileType.INSTANCE

    override fun supportsShortenFQNames(): Boolean = true

    override fun compile(elements: Array<out PsiElement>, globalVisitor: GlobalCompilingVisitor) {
        KotlinCompilingVisitor(globalVisitor).compile(elements)
    }

    override fun createCodeFragment(
        project: Project,
        text: String,
        contextId: String?
    ): PsiCodeFragment? = KtPsiFactory(project).createBlockCodeFragment(text, null)

    override fun getPresentableElement(element: PsiElement): PsiElement {
        val elem = if (isIdentifier(element)) element.parent else return element
        return if(elem is KtReferenceExpression) elem.parent else elem
    }

    override fun isIdentifier(element: PsiElement?): Boolean = element != null && element.node?.elementType == KtTokens.IDENTIFIER

    override fun createPatternTree(
        text: String,
        context: PatternTreeContext,
        fileType: LanguageFileType,
        language: Language,
        contextId: String?,
        project: Project,
        physical: Boolean
    ): Array<PsiElement> {
        var elements: Array<PsiElement>
        val factory = KtPsiFactory(project, false)

        if (PROPERTY_CONTEXT.id == contextId) {
            try {
                val fragment = factory.createProperty(text)
                elements = arrayOf(getNonWhitespaceChildren(fragment).first().parent)
                if (elements.first() !is KtProperty) return PsiElement.EMPTY_ARRAY
            } catch (e: Exception) {
                return arrayOf(factory.createComment("//").apply {
                    putUserData(PATTERN_ERROR, KotlinBundle.message("error.context.getter.or.setter"))
                })
            }
        } else {
            val block = factory.createBlock(text).let {
                if (physical) it else it.copy() as KtBlockExpression // workaround to create non-physical code fragment
            }

            elements = getNonWhitespaceChildren(block).drop(1).dropLast(1).toTypedArray()

            if (block.statements.size == 1) {
                val statement = block.firstStatement
                // Standalone annotation support
                if (statement is KtAnnotatedExpression && statement.lastChild is PsiErrorElement) {
                    elements = getNonWhitespaceChildren(statement).dropLast(1).toTypedArray()
                }
                // Standalone KtNullableType || KtUserType w/ type parameter support
                else if (elements.last() is PsiErrorElement && elements.last().firstChild.elementType == KtTokens.QUEST
                    || elements.first() is KtCallExpression && (elements.first() as KtCallExpression).valueArgumentList == null) {
                    try {
                        elements = arrayOf(factory.createType(text))
                    } catch (_: KotlinExceptionWithAttachments) {}
                }
            }
        }

        //for (element in elements) print(DebugUtil.psiToString(element, false))

        return elements
    }

    inner class KotlinValidator : KotlinRecursiveElementWalkingVisitor() {
        override fun visitErrorElement(element: PsiErrorElement) {
            super.visitErrorElement(element)
            if (shouldShowProblem(element)) {
                throw MalformedPatternException(element.errorDescription)
            }
        }

        override fun visitComment(comment: PsiComment) {
            super.visitComment(comment)
            comment.getUserData(PATTERN_ERROR)?.let { error ->
                throw MalformedPatternException(error)
            }
        }
    }

    override fun checkSearchPattern(pattern: CompiledPattern) {
        val visitor = KotlinValidator()
        val nodes = pattern.nodes
        while (nodes.hasNext()) {
            nodes.current().accept(visitor)
            nodes.advance()
        }
        nodes.reset()
    }

    override fun shouldShowProblem(error: PsiErrorElement): Boolean {
        val description = error.errorDescription
        val parent = error.parent
        return when {
            parent is KtTryExpression && KotlinBundle.message("error.expected.catch.or.finally") == description -> false //naked try
            parent is KtAnnotatedExpression && KotlinBundle.message("error.expected.an.expression") == description -> false
            else -> true
        }
    }

    override fun checkReplacementPattern(project: Project, options: ReplaceOptions) {
        val matchOptions = options.matchOptions
        val fileType = matchOptions.fileType!!
        val dialect = matchOptions.dialect!!
        val searchIsDeclaration = isProbableExpression(matchOptions.searchPattern, fileType, dialect, project)
        val replacementIsDeclaration = isProbableExpression(options.replacement, fileType, dialect, project)
        if (searchIsDeclaration != replacementIsDeclaration) {
            throw UnsupportedPatternException(
                if (searchIsDeclaration) SSRBundle.message("replacement.template.is.not.expression.error.message")
                else SSRBundle.message("search.template.is.not.expression.error.message")
            )
        }
    }

    private fun ancestors(node: PsiElement?): List<PsiElement?> {
        val family = mutableListOf(node)
        repeat(7) { family.add(family.last()?.parent) }
        return family.drop(1)
    }

    override fun isApplicableConstraint(
        constraintName: String,
        variableNode: PsiElement?,
        completePattern: Boolean,
        target: Boolean
    ): Boolean {
        if (variableNode != null)
            return when (constraintName) {
                UIUtil.TYPE, UIUtil.TYPE_REGEX -> isApplicableType(variableNode)
                UIUtil.MINIMUM_ZERO -> isApplicableMinCount(variableNode) || isApplicableMinMaxCount(variableNode)
                UIUtil.MAXIMUM_UNLIMITED -> isApplicableMaxCount(variableNode) || isApplicableMinMaxCount(variableNode)
                UIUtil.TEXT_HIERARCHY -> isApplicableTextHierarchy(variableNode)
                UIUtil.REFERENCE -> isApplicableReference(variableNode)
                AlsoMatchVarModifier.CONSTRAINT_NAME -> variableNode.parent is KtProperty && !(variableNode.parent as KtProperty).isVar
                AlsoMatchValModifier.CONSTRAINT_NAME -> variableNode.parent is KtProperty && (variableNode.parent as KtProperty).isVar
                AlsoMatchCompanionObjectModifier.CONSTRAINT_NAME -> variableNode.parent is KtObjectDeclaration &&
                        !(variableNode.parent as KtObjectDeclaration).isCompanion()
                MatchCallSemanticsModifier.CONSTRAINT_NAME -> variableNode.parent.parent is KtCallElement
                else -> super.isApplicableConstraint(constraintName, variableNode, completePattern, target)
            }

        return super.isApplicableConstraint(constraintName, null as PsiElement?, completePattern, target)
    }

    private fun isApplicableReference(variableNode: PsiElement): Boolean = variableNode.parent is KtNameReferenceExpression

    private fun isApplicableTextHierarchy(variableNode: PsiElement): Boolean {
        val family = ancestors(variableNode)
        return when {
            family[0] is KtClass && (family[0] as KtClass).nameIdentifier == variableNode -> true
            family[0] is KtObjectDeclaration && (family[0] as KtObjectDeclaration).nameIdentifier == variableNode -> true
            family[0] is KtEnumEntry && (family[0] as KtEnumEntry).nameIdentifier == variableNode -> true
            family[0] is KtNamedDeclaration && family[2] is KtClassOrObject -> true
            family[3] is KtSuperTypeListEntry && family[5] is KtClassOrObject -> true
            family[4] is KtSuperTypeListEntry && family[6] is KtClassOrObject -> true
            else -> false
        }
    }

    private fun isApplicableType(variableNode: PsiElement): Boolean {
        val family = ancestors(variableNode)
        return when {
            family[0] is KtNameReferenceExpression -> when (family[1]) {
                is KtValueArgument,
                is KtProperty,
                is KtBinaryExpression, is KtBinaryExpressionWithTypeRHS,
                is KtIsExpression,
                is KtBlockExpression,
                is KtContainerNode,
                is KtArrayAccessExpression,
                is KtPostfixExpression,
                is KtDotQualifiedExpression,
                is KtSafeQualifiedExpression,
                is KtCallableReferenceExpression,
                is KtSimpleNameStringTemplateEntry, is KtBlockStringTemplateEntry,
                is KtPropertyAccessor,
                is KtWhenEntry -> true
                else -> false
            }
            family[0] is KtProperty -> true
            family[0] is KtParameter -> true
            else -> false
        }
    }

    /**
     * Returns true if the largest count filter should be [0; 1].
     */
    private fun isApplicableMinCount(variableNode: PsiElement): Boolean {
        val family = ancestors(variableNode)
        return when {
            family[0] is KtObjectDeclaration -> true
            family[0] !is KtNameReferenceExpression -> false
            family[1] is KtProperty -> true
            family[1] is KtDotQualifiedExpression -> true
            family[1] is KtCallableReferenceExpression && family[0]?.nextSibling.elementType == KtTokens.COLONCOLON -> true
            family[1] is KtWhenExpression -> true
            family[2] is KtTypeReference && family[3] is KtNamedFunction -> true
            family[3] is KtConstructorCalleeExpression -> true
            else -> false
        }
    }

    /**
     * Returns true if the largest count filter should be [1; +inf].
     */
    private fun isApplicableMaxCount(variableNode: PsiElement): Boolean {
        val family = ancestors(variableNode)
        return when {
            family[0] is KtDestructuringDeclarationEntry -> true
            family[0] is KtNameReferenceExpression && family[1] is KtWhenConditionWithExpression -> true
            else -> false
        }
    }

    /**
     * Returns true if the largest count filter should be [0; +inf].
     */
    private fun isApplicableMinMaxCount(variableNode: PsiElement): Boolean {
        val family = ancestors(variableNode)
        return when {
            // Containers (lists, bodies, ...)
            family[0] is KtObjectDeclaration -> false
            family[1] is KtClassBody -> true
            family[0] is KtParameter && family[1] is KtParameterList -> true
            family[0] is KtTypeParameter && family[1] is KtTypeParameterList -> true
            family[2] is KtTypeParameter && family[3] is KtTypeParameterList -> true
            family[1] is KtUserType && family[4] is KtParameterList && family[5] !is KtNamedFunction -> true
            family[1] is KtUserType && family[3] is KtSuperTypeEntry -> true
            family[1] is KtValueArgument && family[2] is KtValueArgumentList -> true
            family[1] is KtBlockExpression && family[3] is KtDoWhileExpression -> true
            family[0] is KtNameReferenceExpression && family[1] is KtBlockExpression -> true
            family[1] is KtUserType && family[3] is KtTypeProjection && family[5] !is KtNamedFunction -> true
            // Annotations
            family[1] is KtUserType && family[4] is KtAnnotationEntry -> true
            family[1] is KtCollectionLiteralExpression -> true
            // Strings
            family[1] is KtSimpleNameStringTemplateEntry -> true
            // KDoc
            family[0] is KDocTag -> true
            // Default: count filter not applicable
            else -> false
        }
    }

    override fun getCustomPredicates(
        constraint: MatchVariableConstraint,
        name: String,
        options: MatchOptions
    ): MutableList<MatchPredicate> {
        val result = SmartList<MatchPredicate>()
        constraint.apply {
            if (nameOfExprType.isNotBlank()) {
                val predicate = KotlinExprTypePredicate(
                    search = if (isRegexExprType) nameOfExprType else expressionTypes,
                    withinHierarchy = isExprTypeWithinHierarchy,
                    ignoreCase = !options.isCaseSensitiveMatch,
                    target = isPartOfSearchResults,
                    baseName = name,
                    regex = isRegexExprType
                )
                result.add(if (isInvertExprType) NotPredicate(predicate) else predicate)
            }
            if (getAdditionalConstraint(AlsoMatchValModifier.CONSTRAINT_NAME) == OneStateFilter.ENABLED ||
                getAdditionalConstraint(AlsoMatchVarModifier.CONSTRAINT_NAME) == OneStateFilter.ENABLED
            ) result.add(KotlinAlsoMatchValVarPredicate())
            if (getAdditionalConstraint(AlsoMatchCompanionObjectModifier.CONSTRAINT_NAME) == OneStateFilter.ENABLED) {
                result.add(KotlinAlsoMatchCompanionObjectPredicate())
            }
            if (getAdditionalConstraint(MatchCallSemanticsModifier.CONSTRAINT_NAME) == OneStateFilter.ENABLED) {
                result.add(KotlinMatchCallSemantics())
            }
        }
        return result
    }

    private fun isProbableExpression(pattern: String, fileType: LanguageFileType, dialect: Language, project: Project): Boolean {
        if(pattern.isEmpty()) return false
        val searchElements = try {
            createPatternTree(pattern, PatternTreeContext.Block, fileType, dialect, null, project, false)
        } catch (e: Exception) { return false }
        if (searchElements.isEmpty()) return false
        return searchElements[0] is KtDeclaration
    }

    override fun getReplaceHandler(project: Project, replaceOptions: ReplaceOptions): KotlinStructuralReplaceHandler =
        KotlinStructuralReplaceHandler(project)

    override fun getPatternContexts(): MutableList<PatternContext> = PATTERN_CONTEXTS

    private fun getNonWhitespaceChildren(fragment: PsiElement): List<PsiElement> {
        var element = fragment.firstChild
        val result: MutableList<PsiElement> = SmartList()
        while (element != null) {
            if (element !is PsiWhiteSpace) result.add(element)
            element = element.nextSibling
        }
        return result
    }

    override fun compileReplacementTypedVariable(name: String): String {
        return TYPED_VAR_PREFIX + name
    }

    override fun isReplacementTypedVariable(name: String): Boolean {
        return name.substring(0, min(TYPED_VAR_PREFIX.length, name.length)) == TYPED_VAR_PREFIX
    }

    override fun stripReplacementTypedVariableDecorations(name: String): String {
        return name.removePrefix(TYPED_VAR_PREFIX)
    }

    override fun provideAdditionalReplaceOptions(node: PsiElement, options: ReplaceOptions, builder: ReplacementBuilder) {
        val profile = this
        node.accept(object : KotlinRecursiveElementVisitor() {
            override fun visitParameter(parameter: KtParameter) {
                val name = parameter.nameIdentifier
                val type = parameter.typeReference ?: return

                val nameInfo = builder.findParameterization(name) ?: return
                nameInfo.isArgumentContext = false
                val infos = mutableMapOf(nameInfo.name to nameInfo)
                nameInfo.putUserData(PARAMETER_CONTEXT, infos)
                nameInfo.element = parameter

                if (profile.isReplacementTypedVariable(type.text)) {
                    val typeInfo = builder.findParameterization(type) ?: return
                    typeInfo.isArgumentContext = false
                    typeInfo.putUserData(PARAMETER_CONTEXT, mapOf(typeInfo.name to typeInfo))
                    infos[typeInfo.name] = typeInfo
                }

                val dflt = parameter.defaultValue ?: return

                if (profile.isReplacementTypedVariable(dflt.text)) {
                    val dfltInfo = builder.findParameterization(dflt) ?: return
                    dfltInfo.isArgumentContext = false
                    dfltInfo.putUserData(PARAMETER_CONTEXT, mapOf(dfltInfo.name to dfltInfo))
                    infos[dfltInfo.name] = dfltInfo
                }
            }
        })
    }

    override fun handleSubstitution(info: ParameterInfo, match: MatchResult, result: StringBuilder, replacementInfo: ReplacementInfo) {
        if (info.name == match.name) {
            val typeInfos = info.getUserData(PARAMETER_CONTEXT)
            if (typeInfos == null) {
                return super.handleSubstitution(info, match, result, replacementInfo)
            }
            if (info.element !is KtParameter) {
                return
            }

            val parameterStart = info.startIndex
            val length = info.element.getTextLength() - typeInfos.keys.sumOf { key: String -> key.length + TYPED_VAR_PREFIX.length }
            val parameterEnd = parameterStart + length
            val template = result.substring(parameterStart, parameterEnd)
            val replacementString = handleParameter(info, replacementInfo, -parameterStart, template)
            result.delete(parameterStart, parameterEnd)

            Replacer.insertSubstitution(result, 0, info, replacementString)
        }
    }

    companion object {
        const val TYPED_VAR_PREFIX: String = "_____"

        val DEFAULT_CONTEXT: PatternContext = PatternContext("default", KotlinBundle.lazyMessage("context.default"))

        val PROPERTY_CONTEXT: PatternContext = PatternContext("property", KotlinBundle.lazyMessage("context.property.getter.or.setter"))

        private val PATTERN_CONTEXTS: MutableList<PatternContext> = mutableListOf(DEFAULT_CONTEXT, PROPERTY_CONTEXT)

        private val PATTERN_ERROR: Key<String> = Key("patternError")

        fun getNonWhitespaceChildren(fragment: PsiElement): List<PsiElement> {
            var element = fragment.firstChild
            val result: MutableList<PsiElement> = SmartList()
            while (element != null) {
                if (element !is PsiWhiteSpace) result.add(element)
                element = element.nextSibling
            }
            return result
        }

        private val PARAMETER_CONTEXT: Key<Map<String, ParameterInfo>> = Key("PARAMETER_CONTEXT")

        private fun appendParameter(parameterInfo: ParameterInfo, matchResult: MatchResult, offset: Int, out: StringBuilder) {
            val infos = checkNotNull(parameterInfo.getUserData(PARAMETER_CONTEXT))
            val matches: MutableList<MatchResult> = SmartList(matchResult.children)
            matches.add(matchResult)
            matches.sortWith(Comparator.comparingInt { result: MatchResult -> result.match.textOffset }.reversed())
            for (match in matches) {
                val typeInfo = infos[match.name]
                if (typeInfo != null) out.insert(typeInfo.startIndex + offset, match.matchImage)
            }
        }

        private fun handleParameter(info: ParameterInfo, replacementInfo: ReplacementInfo, offset: Int, template: String): String {
            val matchResult = checkNotNull(replacementInfo.getNamedMatchResult(info.name))
            val result = StringBuilder()
            if (matchResult.isMultipleMatch) {
                var previous: PsiElement? = null
                for (child in matchResult.children) {
                    val match = child.match.parent
                    if (previous != null) addSeparatorText(previous, match, result)
                    appendParameter(info, child, offset + result.length, result.append(template))
                    previous = match
                }
            } else {
                result.append(template)
                appendParameter(info, matchResult, offset, result)
            }
            return result.toString()
        }

        private fun addSeparatorText(left: PsiElement, right: PsiElement, out: StringBuilder) {
            var e = left.nextSibling
            while (e != null && e !== right) {
                out.append(e.text)
                e = e.nextSibling
            }
        }
    }
}