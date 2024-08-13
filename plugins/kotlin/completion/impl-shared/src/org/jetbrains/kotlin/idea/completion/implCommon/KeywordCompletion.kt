// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.*
import com.intellij.psi.filters.*
import com.intellij.psi.filters.position.LeftNeighbour
import com.intellij.psi.filters.position.PositionElementFilter
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget.*
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.psi.isInsideKtTypeReference
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.idea.completion.handlers.createKeywordConstructLookupElement
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * We want all [KeywordLookupObject]s to be equal to each other.
 *
 * That way, if the same keyword is completed twice, it would not be duplicated in the completion. This is not required in the regular
 * completion, but can be a problem in CodeWithMe plugin (see https://youtrack.jetbrains.com/issue/CWM-438).
 */
open class KeywordLookupObject {
    override fun equals(other: Any?): Boolean = this === other || javaClass == other?.javaClass
    override fun hashCode(): Int = javaClass.hashCode()
}


class KeywordCompletion(private val languageVersionSettingProvider: LanguageVersionSettingProvider) {
    interface LanguageVersionSettingProvider {
        fun getLanguageVersionSetting(element: PsiElement): LanguageVersionSettings
        fun getLanguageVersionSetting(module: Module): LanguageVersionSettings
    }

    companion object {
        private val ALL_KEYWORDS = (KEYWORDS.types + SOFT_KEYWORDS.types)
            .map { it as KtKeywordToken }

        private val INCOMPATIBLE_KEYWORDS_AROUND_SEALED = setOf(
            SEALED_KEYWORD,
            ANNOTATION_KEYWORD,
            DATA_KEYWORD,
            ENUM_KEYWORD,
            OPEN_KEYWORD,
            INNER_KEYWORD,
            ABSTRACT_KEYWORD
        ).mapTo(HashSet()) { it.value }

        private val KEYWORDS_ALLOWED_INSIDE_ANNOTATION_ENTRY: Set<KtKeywordToken> = setOf(
            IF_KEYWORD,
            ELSE_KEYWORD,
            TRUE_KEYWORD,
            FALSE_KEYWORD,
            WHEN_KEYWORD,
        )

        private fun getCompoundKeywords(token: KtKeywordToken, languageVersionSettings: LanguageVersionSettings): Set<KtKeywordToken>? =
            mapOf<KtKeywordToken, Set<KtKeywordToken>>(
                COMPANION_KEYWORD to setOf(OBJECT_KEYWORD),
                DATA_KEYWORD to setOfNotNull(
                    CLASS_KEYWORD,
                    OBJECT_KEYWORD.takeIf { languageVersionSettings.supportsFeature(LanguageFeature.DataObjects) },
                ),
                ENUM_KEYWORD to setOf(CLASS_KEYWORD),
                ANNOTATION_KEYWORD to setOf(CLASS_KEYWORD),
                SEALED_KEYWORD to setOf(CLASS_KEYWORD, INTERFACE_KEYWORD, FUN_KEYWORD),
                LATEINIT_KEYWORD to setOf(VAR_KEYWORD),
                CONST_KEYWORD to setOf(VAL_KEYWORD),
                SUSPEND_KEYWORD to setOf(FUN_KEYWORD)
            )[token]

        private val COMPOUND_KEYWORDS_NOT_SUGGEST_TOGETHER = mapOf<KtKeywordToken, Set<KtKeywordToken>>(
            // 'fun' can follow 'sealed', e.g. "sealed fun interface". But "sealed fun" looks irrelevant differ to "sealed interface/class".
            SEALED_KEYWORD to setOf(FUN_KEYWORD),
        )

        private val KEYWORD_CONSTRUCTS = mapOf<KtKeywordToken, String>(
            IF_KEYWORD to "fun foo() { if (caret)",
            WHILE_KEYWORD to "fun foo() { while(caret)",
            FOR_KEYWORD to "fun foo() { for(caret)",
            TRY_KEYWORD to "fun foo() { try {\ncaret\n}",
            CATCH_KEYWORD to "fun foo() { try {} catch (caret)",
            FINALLY_KEYWORD to "fun foo() { try {\n}\nfinally{\ncaret\n}",
            DO_KEYWORD to "fun foo() { do {\ncaret\n}",
            INIT_KEYWORD to "class C { init {\ncaret\n}",
            CONSTRUCTOR_KEYWORD to "class C { constructor(caret)",
            CONTEXT_KEYWORD to "context(caret)",
        )

        private val NO_SPACE_AFTER = listOf(
            THIS_KEYWORD,
            SUPER_KEYWORD,
            NULL_KEYWORD,
            TRUE_KEYWORD,
            FALSE_KEYWORD,
            BREAK_KEYWORD,
            CONTINUE_KEYWORD,
            ELSE_KEYWORD,
            WHEN_KEYWORD,
            FILE_KEYWORD,
            DYNAMIC_KEYWORD,
            GET_KEYWORD,
            SET_KEYWORD
        ).map { it.value } + "companion object"
    }

    fun complete(position: PsiElement, prefixMatcher: PrefixMatcher, isJvmModule: Boolean, consumer: (LookupElement) -> Unit) {
        if (!GENERAL_FILTER.isAcceptable(position, position)) return
        val sealedInterfacesEnabled = languageVersionSettingProvider.getLanguageVersionSetting(position).supportsFeature(LanguageFeature.SealedInterfaces)

        val parserFilter = buildFilter(position)
        for (keywordToken in ALL_KEYWORDS) {
            val nextKeywords = keywordToken.getNextPossibleKeywords(position) ?: setOf(null)
            nextKeywords.forEach {
                if (keywordToken == SEALED_KEYWORD && it == INTERFACE_KEYWORD && !sealedInterfacesEnabled) return@forEach
                handleCompoundKeyword(position, keywordToken, it, isJvmModule, prefixMatcher, parserFilter, consumer)
            }
        }
    }

    private fun KtKeywordToken.getNextPossibleKeywords(position: PsiElement): Set<KtKeywordToken>? {
        return when {
            this == SUSPEND_KEYWORD && position.isInsideKtTypeReference -> null
            else -> getCompoundKeywords(this, languageVersionSettingProvider.getLanguageVersionSetting(position))
        }
    }

    private fun KtKeywordToken.avoidSuggestingWith(keywordToken: KtKeywordToken): Boolean {
        val nextKeywords = COMPOUND_KEYWORDS_NOT_SUGGEST_TOGETHER[this] ?: return false
        return keywordToken in nextKeywords
    }

    private fun ignorePrefixForKeyword(completionPosition: PsiElement, keywordToken: KtKeywordToken): Boolean =
        when (keywordToken) {
            // it's needed to complete overrides that should work by member name too
            OVERRIDE_KEYWORD -> true

            // keywords that might be used with labels (@label) after them
            THIS_KEYWORD,
            RETURN_KEYWORD,
            BREAK_KEYWORD,
            CONTINUE_KEYWORD -> {
                // If the position is parsed as an expression and has a label, it means that the completion is performed
                // in a place like `return@la<caret>`. The prefix matcher in this case will have its prefix == "la",
                // and it won't match with the keyword text ("return" in this case).
                // That's why we want to ignore the prefix matcher for such positions
                completionPosition is KtExpressionWithLabel && completionPosition.getTargetLabel() != null
            }

            else -> false
        }

    private fun handleCompoundKeyword(
        position: PsiElement,
        keywordToken: KtKeywordToken,
        nextKeyword: KtKeywordToken?,
        isJvmModule: Boolean,
        prefixMatcher: PrefixMatcher,
        parserFilter: (KtKeywordToken) -> Boolean,
        consumer: (LookupElement) -> Unit
    ) {
        if (position.isInsideAnnotationEntryArgumentList() && keywordToken !in KEYWORDS_ALLOWED_INSIDE_ANNOTATION_ENTRY) return

        var keyword = keywordToken.value

        var applicableAsCompound = false
        if (nextKeyword != null) {
            fun PsiElement.isSpace() = this is PsiWhiteSpace && '\n' !in getText()

            var next = position.nextLeaf { !(it.isSpace() || it.text == "$") }?.text
            next = next?.removePrefix("$")

            if (keywordToken == SEALED_KEYWORD) {
                if (next in INCOMPATIBLE_KEYWORDS_AROUND_SEALED) return
                val prev = position.prevLeaf { !(it.isSpace() || it is PsiErrorElement) }?.text
                if (prev in INCOMPATIBLE_KEYWORDS_AROUND_SEALED) return
            }

            val nextIsNotYetPresent = keywordToken.getNextPossibleKeywords(position)?.none { it.value == next } == true
            if (nextIsNotYetPresent && keywordToken.avoidSuggestingWith(nextKeyword)) return

            if (nextIsNotYetPresent)
                keyword += " " + nextKeyword.value
            else
                applicableAsCompound = true
        }

        if (keywordToken == DYNAMIC_KEYWORD && isJvmModule) return // not supported for JVM

        if (!ignorePrefixForKeyword(position, keywordToken) && !prefixMatcher.isStartMatch(keyword)) return

        if (!parserFilter(keywordToken)) return

        val constructText = KEYWORD_CONSTRUCTS[keywordToken]
        if (constructText != null && !applicableAsCompound) {
            val element = createKeywordConstructLookupElement(position.project, keyword, constructText)
            consumer(element)
        } else {
            handleTopLevelClassName(position, keyword, consumer)
            consumer(createLookupElementBuilder(keyword, position))
        }
    }

    private fun handleTopLevelClassName(position: PsiElement, keyword: String, consumer: (LookupElement) -> Unit) {
        val topLevelClassName = getTopLevelClassName(position)
        fun consumeClassNameWithoutBraces() {
            consumer(createLookupElementBuilder("$keyword $topLevelClassName", position))
        }
        if (topLevelClassName != null) {
            if (listOf(OBJECT_KEYWORD, INTERFACE_KEYWORD).any { keyword.endsWith(it.value) }) {
                consumeClassNameWithoutBraces()
            }
            if (keyword.endsWith(CLASS_KEYWORD.value)) {
                if (keyword.startsWith(DATA_KEYWORD.value)) {
                    consumer(createKeywordConstructLookupElement(position.project, keyword, "$keyword $topLevelClassName(caret)"))
                } else {
                    consumeClassNameWithoutBraces()
                }
            }
        }
    }

    private fun createLookupElementBuilder(
        keyword: String,
        position: PsiElement
    ): LookupElementBuilder {
        val isUseSiteAnnotationTarget = position.prevLeaf()?.node?.elementType == AT
        val insertHandler = when {
            isUseSiteAnnotationTarget -> UseSiteAnnotationTargetInsertHandler
            keyword in NO_SPACE_AFTER -> null
            else -> SpaceAfterInsertHandler
        }
        val element = LookupElementBuilder.create(KeywordLookupObject(), keyword).bold().withInsertHandler(insertHandler)
        return if (isUseSiteAnnotationTarget) {
            element.withPresentableText("$keyword:")
        } else {
            element
        }
    }

    private fun getTopLevelClassName(position: PsiElement): String? {
        if (position.parents.any { it is KtDeclaration }) return null
        val file = position.containingFile as? KtFile ?: return null
        val name = FileUtil.getNameWithoutExtension(file.name)
        if (!Name.isValidIdentifier(name)
            || Name.identifier(name).render() != name
            || !name[0].isUpperCase()
            || file.declarations.any { it is KtClassOrObject && it.name == name }
        ) return null
        return name
    }

    private object UseSiteAnnotationTargetInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            WithTailInsertHandler(":", spaceBefore = false, spaceAfter = false).postHandleInsert(context, item)
        }
    }

    private object SpaceAfterInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            WithTailInsertHandler.SPACE.postHandleInsert(context, item)
        }
    }

    private val GENERAL_FILTER = NotFilter(
        OrFilter(
            CommentFilter(),
            ParentFilter(ClassFilter(KtLiteralStringTemplateEntry::class.java)),
            ParentFilter(ClassFilter(KtConstantExpression::class.java)),
            FileFilter(ClassFilter(KtTypeCodeFragment::class.java)),
            LeftNeighbour(TextFilter(".")),
            LeftNeighbour(TextFilter("?."))
        )
    )

    private class CommentFilter : ElementFilter {
        override fun isAcceptable(element: Any?, context: PsiElement?) = (element is PsiElement) && KtPsiUtil.isInComment(element)

        override fun isClassAcceptable(hintClass: Class<out Any?>) = true
    }

    private class ParentFilter(filter: ElementFilter) : PositionElementFilter() {
        init {
            setFilter(filter)
        }

        override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
            val parent = (element as? PsiElement)?.parent
            return parent != null && (filter?.isAcceptable(parent, context) ?: true)
        }
    }

    private class FileFilter(filter: ElementFilter) : PositionElementFilter() {
        init {
            setFilter(filter)
        }

        override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
            val file = (element as? PsiElement)?.containingFile
            return file != null && (filter?.isAcceptable(file, context) ?: true)
        }
    }

    private fun buildFilter(position: PsiElement): (KtKeywordToken) -> Boolean {
        var parent = position.parent
        var prevParent = position
        while (parent != null) {
            when (parent) {
                is KtBlockExpression -> {
                    if (
                        prevParent is KtScriptInitializer &&
                        parent.parent is KtScript &&
                        parent.allChildren.firstIsInstanceOrNull<KtScriptInitializer>() === prevParent &&
                        parent.parent.allChildren.firstIsInstanceOrNull<KtBlockExpression>() === parent
                    ) {
                        // It's the first script initializer after preamble.
                        // Possibly, user enters "import" or "package" here. So we need a global context for it.
                        return buildFilterWithReducedContext("", null, position)
                    }

                    var prefixText = "fun foo() { "
                    if (prevParent is KtExpression) {
                        // check that we are right after a try-expression without finally-block or after if-expression without else
                        val prevLeaf = prevParent.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment && it !is PsiErrorElement }
                        if (prevLeaf != null) {
                            val isAfterThen = prevLeaf.goUpWhileIsLastChild().any { it.node.elementType == KtNodeTypes.THEN }

                            var isAfterTry = false
                            var isAfterCatch = false
                            if (prevLeaf.node.elementType == RBRACE) {
                                when ((prevLeaf.parent as? KtBlockExpression)?.parent) {
                                    is KtTryExpression -> isAfterTry = true
                                    is KtCatchClause -> {
                                        isAfterTry = true; isAfterCatch = true
                                    }
                                }
                            }

                            if (isAfterThen) {
                                prefixText += if (isAfterTry) {
                                    "if (a)\n"
                                } else {
                                    "if (a) {}\n"
                                }
                            }
                            if (isAfterTry) {
                                prefixText += "try {}\n"
                            }
                            if (isAfterCatch) {
                                prefixText += "catch (e: E) {}\n"
                            }
                        }

                        return buildFilterWithContext(prefixText, prevParent, position)
                    } else {
                        val lastExpression = prevParent
                            .siblings(forward = false, withItself = false)
                            .firstIsInstanceOrNull<KtExpression>()
                        if (lastExpression != null) {
                            val contextAfterExpression = lastExpression
                                .siblings(forward = true, withItself = false)
                                .takeWhile { it != prevParent }
                                .joinToString { it.text }
                            return buildFilterWithContext(prefixText + "x" + contextAfterExpression, prevParent, position)
                        }
                    }
                }

                is KtDeclarationWithInitializer -> {
                    val initializer = parent.initializer
                    if (prevParent == initializer) {
                        return buildFilterWithContext("val v = ", initializer, position)
                    }
                }

                is KtParameter -> {
                    val default = parent.defaultValue
                    if (prevParent == default) {
                        return buildFilterWithContext("val v = ", default, position)
                    }
                }

                // for type references in places like 'listOf<' or 'List<' we want to filter almost all keywords
                // (except maybe for 'suspend' and 'in'/'out', since they can be a part of a type reference)
                is KtTypeReference -> {
                    val shouldIntroduceTypeReferenceContext = when {

                        // it can be a receiver type, or it can be a declaration's name,
                        // so we don't want to change the context
                        parent.isExtensionReceiverInCallableDeclaration -> false

                        // it is probably an annotation entry, or a super class constructor's invocation,
                        // in this case we don't want to change the context
                        parent.parent is KtConstructorCalleeExpression -> false

                        else -> true
                    }

                    if (shouldIntroduceTypeReferenceContext) {

                        // we cannot just search for an outer element of KtTypeReference type, because
                        // we can be inside the lambda type args (e.g. 'val foo: (bar: <caret>) -> Unit');
                        // that's why we have to do a more precise check
                        val prefixText = if (parent.isTypeArgumentOfOuterKtTypeReference) {
                            "fun foo(x: X<"
                        } else {
                            "fun foo(x: "
                        }

                        return buildFilterWithContext(prefixText, contextElement = parent, position)
                    }
                }

                is KtDeclaration -> {
                    when (parent.parent) {
                        is KtClassOrObject -> {
                            return if (parent is KtPrimaryConstructor) {
                                buildFilterWithReducedContext("class X ", parent, position)
                            } else {
                                buildFilterWithReducedContext("class X { ", parent, position)
                            }
                        }

                        is KtFile -> return buildFilterWithReducedContext("", parent, position)
                    }
                }
            }


            prevParent = parent
            parent = parent.parent
        }

        return buildFilterWithReducedContext("", null, position)
    }

    private val KtTypeReference.isExtensionReceiverInCallableDeclaration: Boolean
        get() {
            val parent = parent
            return parent is KtCallableDeclaration && parent.receiverTypeReference == this
        }

    private val KtTypeReference.isTypeArgumentOfOuterKtTypeReference: Boolean
        get() {
            val typeProjection = parent as? KtTypeProjection
            val typeArgumentList = typeProjection?.parent as? KtTypeArgumentList
            val userType = typeArgumentList?.parent as? KtUserType

            return userType?.parent is KtTypeReference
        }

    private fun computeKeywordApplications(prefixText: String, keyword: KtKeywordToken): Sequence<String> = when (keyword) {
        SUSPEND_KEYWORD -> sequenceOf("suspend () -> Unit>", "suspend X")
        else -> {
            if (prefixText.endsWith("@"))
                sequenceOf(keyword.value + ":X Y.Z")
            else
                sequenceOf(keyword.value + " X")
        }
    }

    private fun buildFilterWithContext(
        prefixText: String,
        contextElement: PsiElement,
        position: PsiElement
    ): (KtKeywordToken) -> Boolean {
        val offset = position.getStartOffsetInAncestor(contextElement)
        val truncatedContext = contextElement.text!!.substring(0, offset)
        return buildFilterByText(prefixText + truncatedContext, position)
    }

    private fun buildFilterWithReducedContext(
        prefixText: String,
        contextElement: PsiElement?,
        position: PsiElement
    ): (KtKeywordToken) -> Boolean {
        val builder = StringBuilder()
        buildReducedContextBefore(builder, position, contextElement)
        return buildFilterByText(prefixText + builder.toString(), position)
    }


    private fun buildFilesWithKeywordApplication(
        keywordTokenType: KtKeywordToken,
        prefixText: String,
        psiFactory: KtPsiFactory
    ): Sequence<KtFile> {
        return computeKeywordApplications(prefixText, keywordTokenType)
            .map { application -> psiFactory.createFile(prefixText + application) }
    }


    private fun buildFilterByText(prefixText: String, position: PsiElement): (KtKeywordToken) -> Boolean {
        val psiFactory = KtPsiFactory(position.project)

        fun PsiElement.isSecondaryConstructorInObjectDeclaration(): Boolean {
            val secondaryConstructor = parentOfType<KtSecondaryConstructor>() ?: return false
            return secondaryConstructor.getContainingClassOrObject() is KtObjectDeclaration
        }

        fun isKeywordCorrectlyApplied(keywordTokenType: KtKeywordToken, file: KtFile): Boolean {
            val elementAt = file.findElementAt(prefixText.length)!!

            val languageVersionSettings =
                ModuleUtilCore.findModuleForPsiElement(position)?.let(languageVersionSettingProvider::getLanguageVersionSetting)
                    ?: LanguageVersionSettingsImpl.DEFAULT
            when {
                !elementAt.node!!.elementType.matchesKeyword(keywordTokenType) -> return false

                elementAt.getNonStrictParentOfType<PsiErrorElement>() != null -> return false

                isErrorElementBefore(elementAt) -> return false

                !isModifierSupportedAtLanguageLevel(elementAt, keywordTokenType, languageVersionSettings) -> return false

                (keywordTokenType == VAL_KEYWORD || keywordTokenType == VAR_KEYWORD) &&
                        elementAt.parent is KtParameter &&
                        elementAt.parentOfTypes(KtNamedFunction::class, KtSecondaryConstructor::class) != null -> return false

                keywordTokenType == CONSTRUCTOR_KEYWORD && elementAt.isSecondaryConstructorInObjectDeclaration() -> return false

                keywordTokenType !is KtModifierKeywordToken -> return true

                else -> {
                    val container = (elementAt.parent as? KtModifierList)?.parent ?: return true
                    val possibleTargets = when (container) {
                        is KtParameter -> {
                            if (container.ownerFunction is KtPrimaryConstructor)
                                listOf(VALUE_PARAMETER, MEMBER_PROPERTY)
                            else
                                listOf(VALUE_PARAMETER)
                        }

                        is KtTypeParameter -> listOf(TYPE_PARAMETER)

                        is KtEnumEntry -> listOf(ENUM_ENTRY)

                        is KtClassBody -> listOf(
                            CLASS_ONLY,
                            INTERFACE,
                            OBJECT,
                            ENUM_CLASS,
                            ANNOTATION_CLASS,
                            MEMBER_FUNCTION,
                            MEMBER_PROPERTY,
                            FUNCTION,
                            PROPERTY
                        )

                        is KtFile -> listOf(
                            CLASS_ONLY,
                            INTERFACE,
                            OBJECT,
                            ENUM_CLASS,
                            ANNOTATION_CLASS,
                            TOP_LEVEL_FUNCTION,
                            TOP_LEVEL_PROPERTY,
                            FUNCTION,
                            PROPERTY
                        )

                        else -> listOf()
                    }
                    val modifierTargets = possibleTargetMap[keywordTokenType]?.intersect(possibleTargets)
                    if (modifierTargets != null && possibleTargets.isNotEmpty() &&
                        modifierTargets.none {
                            isModifierTargetSupportedAtLanguageLevel(keywordTokenType, it, languageVersionSettings)
                        }
                    ) return false

                    val parentTarget = when (val ownerDeclaration = container?.getParentOfType<KtDeclaration>(strict = true)) {
                        null -> FILE

                        is KtClass -> {
                            when {
                                ownerDeclaration.isInterface() -> INTERFACE
                                ownerDeclaration.isEnum() -> ENUM_CLASS
                                ownerDeclaration.isAnnotation() -> ANNOTATION_CLASS
                                else -> CLASS_ONLY
                            }
                        }

                        is KtObjectDeclaration -> if (ownerDeclaration.isObjectLiteral()) OBJECT_LITERAL else OBJECT

                        else -> return keywordTokenType != CONST_KEYWORD
                    }

                    if (!isPossibleParentTarget(keywordTokenType, parentTarget, languageVersionSettings)) return false

                    if (keywordTokenType == CONST_KEYWORD) {
                        return when (parentTarget) {
                            OBJECT -> true
                            FILE -> {
                                val prevSiblings = elementAt.parent.siblings(withItself = false, forward = false)
                                val hasLineBreak = prevSiblings
                                    .takeWhile { it is PsiWhiteSpace || it.isSemicolon() }
                                    .firstOrNull { it.text.contains("\n") || it.isSemicolon() } != null
                                hasLineBreak || prevSiblings.none {
                                    it !is PsiWhiteSpace && !it.isSemicolon() && it !is KtImportList && it !is KtPackageDirective
                                }
                            }
                            else -> false
                        }
                    }

                    return true
                }
            }
        }

        return fun(keywordTokenType): Boolean {
            val files = buildFilesWithKeywordApplication(keywordTokenType, prefixText, psiFactory)
            return files.any { file -> isKeywordCorrectlyApplied(keywordTokenType, file); }
        }
    }

    private fun PsiElement.isSemicolon() = node.elementType == SEMICOLON

    private fun isErrorElementBefore(token: PsiElement): Boolean {
        for (leaf in token.prevLeafs) {
            if (leaf is PsiWhiteSpace || leaf is PsiComment) continue
            if (leaf.parentsWithSelf.any { it is PsiErrorElement }) return true
            if (leaf.textLength != 0) break
        }
        return false
    }

    private fun IElementType.matchesKeyword(keywordType: KtKeywordToken): Boolean {
        return when (this) {
            keywordType -> true
            NOT_IN -> keywordType == IN_KEYWORD
            NOT_IS -> keywordType == IS_KEYWORD
            else -> false
        }
    }

    private fun isModifierSupportedAtLanguageLevel(
        position: PsiElement,
        keyword: KtKeywordToken,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        val feature = when (keyword) {
            TYPE_ALIAS_KEYWORD -> LanguageFeature.TypeAliases
            EXPECT_KEYWORD, ACTUAL_KEYWORD -> LanguageFeature.MultiPlatformProjects
            SUSPEND_KEYWORD -> LanguageFeature.Coroutines
            FIELD_KEYWORD -> {
                if (!position.isExplicitBackingFieldDeclaration) return true

                LanguageFeature.ExplicitBackingFields
            }
            CONTEXT_KEYWORD -> LanguageFeature.ContextReceivers
            else -> return true
        }
        return languageVersionSettings.supportsFeature(feature)
    }

    private fun isModifierTargetSupportedAtLanguageLevel(
        keyword: KtKeywordToken,
        target: KotlinTarget,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        if (keyword == LATEINIT_KEYWORD) {
            val feature = when (target) {
                TOP_LEVEL_PROPERTY -> LanguageFeature.LateinitTopLevelProperties
                LOCAL_VARIABLE -> LanguageFeature.LateinitLocalVariables
                else -> return true
            }
            return languageVersionSettings.supportsFeature(feature)
        } else {
            return true
        }
    }

    // builds text within scope (or from the start of the file) before position element excluding almost all declarations
    private fun buildReducedContextBefore(builder: StringBuilder, position: PsiElement, scope: PsiElement?) {
        if (position == scope) return

        if (position is KtCodeFragment) {
            val ktContext = position.context as? KtElement ?: return
            buildReducedContextBefore(builder, ktContext, scope)
            return
        } else if (position is PsiFile) {
            return
        }

        val parent = position.parent ?: return

        buildReducedContextBefore(builder, parent, scope)

        val prevDeclaration = position.siblings(forward = false, withItself = false).firstOrNull { it is KtDeclaration }

        var child = parent.firstChild
        while (child != position) {
            if (child is KtDeclaration) {
                if (child == prevDeclaration) {
                    builder.appendReducedText(child)
                }
            } else {
                builder.append(child!!.text)
            }

            child = child.nextSibling
        }
    }

    private fun StringBuilder.appendReducedText(element: PsiElement) {
        var child = element.firstChild
        if (child == null) {
            append(element.text!!)
        } else {
            while (child != null) {
                when (child) {
                    is KtBlockExpression, is KtClassBody -> append("{}")
                    else -> appendReducedText(child)
                }

                child = child.nextSibling
            }
        }
    }

    private fun PsiElement.getStartOffsetInAncestor(ancestor: PsiElement): Int {
        if (ancestor == this) return 0
        return parent!!.getStartOffsetInAncestor(ancestor) + startOffsetInParent
    }

    private fun PsiElement.goUpWhileIsLastChild(): Sequence<PsiElement> = generateSequence(this) {
        when {
            it is PsiFile -> null
            it != it.parent.lastChild -> null
            else -> it.parent
        }
    }

    private fun isPossibleParentTarget(
        modifier: KtModifierKeywordToken,
        parentTarget: KotlinTarget,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        deprecatedParentTargetMap[modifier]?.let {
            if (parentTarget in it) return false
        }

        possibleParentTargetPredicateMap[modifier]?.let {
            return it.isAllowed(parentTarget, languageVersionSettings)
        }

        return true
    }

    private val PsiElement.isExplicitBackingFieldDeclaration
        get() = parent is KtBackingField
}