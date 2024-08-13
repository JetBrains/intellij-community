// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.IndentHelper
import com.intellij.psi.util.elementType
import com.intellij.structuralsearch.StructuralReplaceHandler
import com.intellij.structuralsearch.StructuralSearchUtil
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.addTypeParameter
import org.jetbrains.kotlin.idea.core.setDefaultValue
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.js.translate.declaration.hasCustomSetter
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference

class KotlinStructuralReplaceHandler(private val project: Project) : StructuralReplaceHandler() {
    override fun replace(info: ReplacementInfo, options: ReplaceOptions) {
        val searchTemplate = StructuralSearchUtil.getPresentableElement(
            PatternCompiler.compilePattern(project, options.matchOptions, true, true).let { it.targetNode ?: it.nodes.current() })
        val fileType = options.matchOptions.fileType ?: return
        val replaceTemplates = MatcherImplUtil.createTreeFromText(
            info.replacement.fixPattern(), PatternTreeContext.Block, fileType, project
        )
        for (i in 0 until info.matchesCount) {
            val match = StructuralSearchUtil.getPresentableElement(info.getMatch(i)) ?: break
            val replacement = replaceTemplates.getOrNull(i) ?: break
            replacement.structuralReplace(searchTemplate, StructuralSearchUtil.getPresentableElement(info.matchResult.match), options)
            match.replace(replacement)
        }
        var lastElement = info.getMatch(info.matchesCount - 1) ?: return
        (info.matchesCount until replaceTemplates.size).forEach { i ->
            val replacement = replaceTemplates[i]
            if (replacement is PsiErrorElement) return@forEach
            lastElement.parent.addAfter(replacement, lastElement)
            lastElement.nextSibling?.let { next ->
                lastElement = next.reformatted()
            }
        }
        (replaceTemplates.size until info.matchesCount).mapNotNull(info::getMatch).forEach {
            val parent = it.parent
            StructuralSearchUtil.getPresentableElement(it).delete()
            if (parent is KtBlockExpression) { // fix formatting for right braces when deleting
                parent.rBrace?.reformatted()
            }
        }
    }

    override fun postProcess(affectedElement: PsiElement, options: ReplaceOptions) {
        if (options.isToShortenFQN) {
            ShortenReferences.DEFAULT.process(affectedElement as KtElement)
        }
    }

    private fun String.fixPattern(): String {
        if (startsWith(".")) return substring(startIndex = 1) // dot qualified expressions without receiver matching normal call
        return this
    }

    private fun PsiElement.structuralReplace(searchTemplate: PsiElement, match: PsiElement, options: ReplaceOptions): PsiElement {
        if (searchTemplate is KtDeclaration && this is KtDeclaration && match is KtDeclaration) {
            replaceDeclaration(searchTemplate, match)
            if (this is KtCallableDeclaration && searchTemplate is KtCallableDeclaration && match is KtCallableDeclaration) {
                replaceCallableDeclaration(searchTemplate, match)
            }
            when {
                this is KtClassOrObject && searchTemplate is KtClassOrObject && match is KtClassOrObject -> replaceClassOrObject(
                    searchTemplate, match
                )
                this is KtNamedFunction && searchTemplate is KtNamedFunction && match is KtNamedFunction -> replaceNamedFunction(
                    searchTemplate, match
                )
                this is KtProperty && searchTemplate is KtProperty && match is KtProperty -> replaceProperty(searchTemplate, match)
            }
        } else { // KtExpression
            when {
                this is KtWhenExpression -> replaceWhenExpression()
                this is KtLambdaExpression && searchTemplate is KtLambdaExpression && match is KtLambdaExpression -> replaceLambdaExpression(
                    searchTemplate, match
                )
                this is KtDotQualifiedExpression && searchTemplate is KtDotQualifiedExpression && match is KtDotQualifiedExpression -> replaceDotQualifiedExpression(
                    searchTemplate, match, options
                )
                this is KtCallExpression && searchTemplate is KtCallExpression && match is KtCallExpression -> replaceCallExpression(
                    searchTemplate, match
                )
            }
            fixWhiteSpace(match)
        }
        return this
    }

    private fun KtCallExpression.replaceCallExpression(searchTemplate: KtCallExpression, match: KtCallExpression) {
        lambdaArguments.firstOrNull()?.getLambdaExpression()?.replaceLambdaExpression(
            searchTemplate.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return,
            match.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        )
    }

    private fun KtDotQualifiedExpression.replaceDotQualifiedExpression(
        searchTemplate: KtDotQualifiedExpression, match: KtDotQualifiedExpression, options: ReplaceOptions
    ) {
        receiverExpression.structuralReplace(searchTemplate.receiverExpression, match.receiverExpression, options)
        val selectorExpr = selectorExpression
        val searchSelectorExpr = searchTemplate.selectorExpression
        val matchSelectorExpr = match.selectorExpression
        if (selectorExpr != null && searchSelectorExpr != null && matchSelectorExpr != null) {
            selectorExpr.structuralReplace(searchSelectorExpr, matchSelectorExpr, options)
        }
    }

    private fun KtWhenExpression.replaceWhenExpression() {
        if (subjectExpression == null) {
            leftParenthesis?.delete()
            rightParenthesis?.delete()
        }
    }

    private fun KtLambdaExpression.replaceLambdaExpression(searchTemplate: KtLambdaExpression, match: KtLambdaExpression) {
        if (valueParameters.isEmpty() && searchTemplate.valueParameters.isEmpty()) { // { $A$ } templates
            match.functionLiteral.valueParameterList?.let {
                functionLiteral.addAfter(it, functionLiteral.lBrace)
                functionLiteral.valueParameterList?.let { vPl ->
                    match.functionLiteral.arrow?.let { mArrow -> functionLiteral.addAfter(mArrow, vPl) }
                    match.functionLiteral.valueParameterList?.let { mVpl -> functionLiteral.addSurroundingWhiteSpace(vPl, mVpl) }
                }
            }
        }
        if (valueParameters.isEmpty()) {
            findDescendantOfType<PsiElement> { it.elementType == KtTokens.ARROW }?.let {
                it.deleteSurroundingWhitespace()
                it.delete()
            }
        }
        valueParameters.forEachIndexed { i, par ->
            val searchPar = searchTemplate.valueParameters.getOrElse(i) { return@forEachIndexed }
            val matchPar = match.valueParameters.getOrElse(i) { return@forEachIndexed }
            if (par.typeReference == null && searchPar.typeReference == null) {
                matchPar.typeReference?.let { mTr ->
                    par.typeReference = mTr
                    par.typeReference?.let { pTr -> par.addSurroundingWhiteSpace(pTr, mTr) }
                }
            }
        }
    }

    private fun PsiElement.fixWhiteSpace(match: PsiElement) {
        val indentationLength = IndentHelper.getInstance().getIndent(match.containingFile, match.node, true)
        collectDescendantsOfType<PsiWhiteSpace> { it.text.contains("\n") }.forEach {
            val newLineCount = it.text.count { char -> char == '\n' }
            it.replace(KtPsiFactory(project).createWhiteSpace(
                "\n".repeat(newLineCount) + " ".repeat(indentationLength + it.text.length - 1))
            )
        }
    }

    private fun KtModifierListOwner.replaceModifier(
        searchTemplate: KtModifierListOwner, match: KtModifierListOwner, modifier: KtModifierKeywordToken
    ): KtModifierListOwner {
        if (!hasModifier(modifier) && match.hasModifier(modifier) && !searchTemplate.hasModifier(modifier)) {
            addModifier(modifier)
            modifierList?.getModifier(modifier)?.let { mod ->
                match.modifierList?.getModifier(modifier)?.let { mMod ->
                    modifierList?.addSurroundingWhiteSpace(mod, mMod)
                }
            }
        }
        return this
    }

    private fun KtModifierListOwner.fixModifierListFormatting(match: KtModifierListOwner): KtModifierListOwner {
        modifierList?.children?.let { children ->
            if (children.isNotEmpty() && children.last() is PsiWhiteSpace) children.last().delete()
        }
        modifierList?.let { rModL ->
            match.modifierList?.let { mModL ->
                addSurroundingWhiteSpace(rModL, mModL)
            }
        }
        return this
    }

    private fun KtDeclaration.replaceDeclaration(searchTemplate: KtDeclaration, match: KtDeclaration): KtDeclaration {
        if (modifierList?.annotationEntries?.isEmpty() == true) { // remove @ symbol for when annotation count filter is equal to 0
            val atElement = modifierList?.children?.find { it is PsiErrorElement }
            atElement?.delete()
        }
        val annotationNames = annotationEntries.map { it.shortName }
        val searchNames = searchTemplate.annotationEntries.map { it.shortName }
        match.annotationEntries.forEach { matchAnnotation ->
            val shortName = matchAnnotation.shortName
            if (!annotationNames.contains(shortName) && !searchNames.contains(shortName)) {
                addAnnotationEntry(matchAnnotation)
            }
        }

        fun KtDeclaration.replaceVisibilityModifiers(searchTemplate: KtDeclaration, match: KtDeclaration): PsiElement {
            if (visibilityModifierType() == null && searchTemplate.visibilityModifierType() == null) {
                match.visibilityModifierType()?.let {
                    addModifier(it)
                    visibilityModifier()?.let { vM ->
                        match.visibilityModifier()?.let { mVm ->
                            modifierList?.addSurroundingWhiteSpace(vM, mVm)
                        }
                    }
                }
            }
            return this
        }
        replaceVisibilityModifiers(searchTemplate, match)
        return this
    }

    private fun KtCallableDeclaration.replaceCallableDeclaration(
        searchTemplate: KtCallableDeclaration, match: KtCallableDeclaration
    ): KtCallableDeclaration {
        if (receiverTypeReference == null && searchTemplate.receiverTypeReference == null) {
            match.receiverTypeReference?.let(this::setReceiverTypeReference)
        }
        if (typeReference == null || searchTemplate.typeReference == null) {
            match.typeReference?.let { matchTr ->
                typeReference = matchTr
                typeReference?.let { addSurroundingWhiteSpace(it, matchTr) }
                colon?.let { c -> match.colon?.let { mC -> addSurroundingWhiteSpace(c, mC) } }
            }
        }
        val searchParam = searchTemplate.valueParameterList
        val matchParam = match.valueParameterList
        if (searchParam != null && matchParam != null) valueParameterList?.replaceParameterList(searchParam, matchParam)
        if (typeParameterList == null && searchTemplate.typeParameterList == null) match.typeParameters.forEach {
            addTypeParameter(it)
        }
        return this
    }

    private fun KtClassOrObject.replaceClassOrObject(searchTemplate: KtClassOrObject, match: KtClassOrObject): KtClassOrObject {
        if (getSuperTypeList()?.findDescendantOfType<PsiErrorElement>() != null) {
            getSuperTypeList()?.delete()
        }
        if (typeParameters.isEmpty()) typeParameterList?.delete() // for count filter equals to 0 inside <>

        CLASS_MODIFIERS.forEach { replaceModifier(searchTemplate, match, it) }
        fixModifierListFormatting(match)

        if (primaryConstructor == null && searchTemplate.primaryConstructor == null) {
            match.primaryConstructor?.let { addFormatted(it) }
        }

        if (primaryConstructorModifierList == null && searchTemplate.primaryConstructorModifierList == null) {
            match.primaryConstructorModifierList?.let { matchModList ->
                matchModList.visibilityModifierType()?.let { primaryConstructor?.addModifier(it) }
                primaryConstructor?.let { pC -> match.primaryConstructor?.let { mPc -> addSurroundingWhiteSpace(pC, mPc) } }

            }
        }

        val searchParamList = searchTemplate.getPrimaryConstructorParameterList()
        val matchParamList = match.getPrimaryConstructorParameterList()
        if (searchParamList != null && matchParamList != null) getPrimaryConstructorParameterList()?.replaceParameterList(
            searchParamList, matchParamList
        )

        if (getSuperTypeList() == null && searchTemplate.getSuperTypeList() == null) { // replace all entries
            match.superTypeListEntries.forEach {
                val superTypeEntry = addSuperTypeListEntry(it)
                getSuperTypeList()?.addSurroundingWhiteSpace(superTypeEntry, it)
            }

            // format commas
            val matchCommas =
                match.getSuperTypeList()?.node?.children()?.filter { it.elementType == KtTokens.COMMA }?.map { it.psi }?.toList()
            getSuperTypeList()?.node?.children()?.filter { it.elementType == KtTokens.COMMA }?.forEachIndexed { index, element ->
                matchCommas?.get(index)?.let { mC -> getSuperTypeList()?.addSurroundingWhiteSpace(element.psi, mC) }
            }

            // format semi colon
            if (superTypeListEntries.isNotEmpty() && match.superTypeListEntries.isNotEmpty()) {
                getColon()?.let { replaceColon ->
                    match.getColon()?.let { matchColon ->
                        addSurroundingWhiteSpace(replaceColon, matchColon)
                    }
                }
            }
        }
        getSuperTypeList()?.node?.lastChildNode?.psi?.let { if (it is PsiWhiteSpace) it.delete() }
        if (body == null && searchTemplate.body == null) match.body?.let { matchBody ->
            addFormatted(matchBody)
        }
        return this
    }

    private fun KtNamedFunction.replaceNamedFunction(searchTemplate: KtNamedFunction, match: KtNamedFunction): KtNamedFunction {
        FUN_MODIFIERS.forEach { replaceModifier(searchTemplate, match, it) }
        fixModifierListFormatting(match)
        if (receiverTypeReference?.findDescendantOfType<PsiErrorElement> { true } != null) {
            findDescendantOfType<PsiElement> { it.elementType == KtTokens.DOT }?.delete()
        }
        if (!hasBody() && !searchTemplate.hasBody()) {
            match.equalsToken?.let { addFormatted(it) }
            match.bodyExpression?.let { addFormatted(it) }
        }
        if (lastChild !is PsiComment && searchTemplate.lastChild !is PsiComment && match.lastChild is PsiComment) {
            match.lastChild?.let { addFormatted(it) }
        }
        return this
    }

    private fun KtProperty.replaceProperty(searchTemplate: KtProperty, match: KtProperty): KtProperty {
        if (initializer == null) equalsToken?.let {  // when count filter = 0 on the initializer
            it.deleteSurroundingWhitespace()
            it.delete()
        }
        PROPERTY_MODIFIERS.forEach { replaceModifier(searchTemplate, match, it) }
        fixModifierListFormatting(match)
        if (!hasDelegate() && !hasInitializer()) {
            if (!searchTemplate.hasInitializer()) {
                match.equalsToken?.let { addFormatted(it) }
                match.initializer?.let { addFormatted(it) }
            }
            if (!searchTemplate.hasDelegate()) match.delegate?.let { addFormatted(it) }
        }
        if (!hasCustomGetter() && !searchTemplate.hasCustomGetter()) match.getter?.let { addFormatted(it) }
        if (!hasCustomSetter() && !searchTemplate.hasCustomSetter()) match.setter?.let { addFormatted(it) }
        return this
    }

    private fun KtParameterList.replaceParameterList(
        searchTemplate: KtParameterList, match: KtParameterList
    ): KtParameterList {
        parameters.forEachIndexed { i, param ->
            val searchParam = searchTemplate.parameters.getOrNull(i)
            val matchParam = match.parameters.getOrNull(i) ?: return@forEachIndexed
            if (searchParam == null) { // count filter
                addSurroundingWhiteSpace(param, matchParam)
            }
            if (param.valOrVarKeyword == null && searchParam?.valOrVarKeyword == null) {
                matchParam.valOrVarKeyword?.let { mVal ->
                    param.addBefore(mVal, param.nameIdentifier)
                    param.valOrVarKeyword?.let { param.addSurroundingWhiteSpace(it, mVal) }
                }
            }
            if (param.typeReference == null && searchParam?.typeReference == null) {
                matchParam.typeReference?.let {
                    param.typeReference = it
                    param.colon?.let { pColon -> matchParam.colon?.let { mColon -> param.addSurroundingWhiteSpace(pColon, mColon) } }
                }
            }
            if (!param.hasDefaultValue() && searchParam?.hasDefaultValue() != true) {
                matchParam.defaultValue?.let {
                    param.setDefaultValue(it)
                    param.equalsToken?.let { pEq -> matchParam.equalsToken?.let { mEq -> param.addSurroundingWhiteSpace(pEq, mEq) } }
                }
            }
        }
        return this
    }

    private fun PsiElement.addFormatted(match: PsiElement) = addSurroundingWhiteSpace(add(match), match)

    private fun PsiElement.addSurroundingWhiteSpace(anchor: PsiElement, match: PsiElement) {
        val nextAnchor = anchor.nextSibling
        val prevAnchor = anchor.prevSibling
        val nextElement = match.nextSibling
        val prevElement = match.prevSibling
        if (prevElement is PsiWhiteSpace) {
            if (prevAnchor is PsiWhiteSpace) prevAnchor.replace(prevElement)
            else addBefore(prevElement, anchor)

        }
        if (nextElement is PsiWhiteSpace) {
            if (nextAnchor is PsiWhiteSpace) nextAnchor.replace(nextElement)
            else addAfter(nextElement, anchor)
        }
    }

    private fun PsiElement.deleteSurroundingWhitespace() {
        val nextAnchor = nextSibling
        val prevAnchor = prevSibling
        if (nextAnchor is PsiWhiteSpace) nextAnchor.delete()
        if (prevAnchor is PsiWhiteSpace) prevAnchor.delete()
    }

    companion object {
        private val CLASS_MODIFIERS = arrayOf(
            KtTokens.ABSTRACT_KEYWORD,
            KtTokens.ENUM_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.INNER_KEYWORD,
            KtTokens.FINAL_KEYWORD,
            KtTokens.COMPANION_KEYWORD,
            KtTokens.SEALED_KEYWORD,
            KtTokens.DATA_KEYWORD,
            KtTokens.INLINE_KEYWORD,
            KtTokens.EXTERNAL_KEYWORD,
            KtTokens.ANNOTATION_KEYWORD,
            KtTokens.CROSSINLINE_KEYWORD,
            KtTokens.EXPECT_KEYWORD,
            KtTokens.ACTUAL_KEYWORD
        )

        private val FUN_MODIFIERS = arrayOf(
            KtTokens.ABSTRACT_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.INNER_KEYWORD,
            KtTokens.OVERRIDE_KEYWORD,
            KtTokens.FINAL_KEYWORD,
            KtTokens.INLINE_KEYWORD,
            KtTokens.TAILREC_KEYWORD,
            KtTokens.EXTERNAL_KEYWORD,
            KtTokens.OPERATOR_KEYWORD,
            KtTokens.INFIX_KEYWORD,
            KtTokens.SUSPEND_KEYWORD,
            KtTokens.EXPECT_KEYWORD,
            KtTokens.ACTUAL_KEYWORD
        )

        private val PROPERTY_MODIFIERS = arrayOf(
            KtTokens.ABSTRACT_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.OVERRIDE_KEYWORD,
            KtTokens.FINAL_KEYWORD,
            KtTokens.LATEINIT_KEYWORD,
            KtTokens.EXTERNAL_KEYWORD,
            KtTokens.CONST_KEYWORD,
            KtTokens.EXPECT_KEYWORD,
            KtTokens.ACTUAL_KEYWORD
        )
    }
}