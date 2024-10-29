// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.DependentSpacingRule
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.util.requireNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.psi.psiUtil.textRangeWithoutComments

val MODIFIERS_LIST_ENTRIES = TokenSet.orSet(TokenSet.create(KtNodeTypes.ANNOTATION_ENTRY, KtNodeTypes.ANNOTATION),
                                            KtTokens.MODIFIER_KEYWORDS)
val EXTEND_COLON_ELEMENTS =
    TokenSet.create(KtNodeTypes.TYPE_CONSTRAINT, KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION, KtNodeTypes.TYPE_PARAMETER,
                    KtNodeTypes.ENUM_ENTRY, KtNodeTypes.SECONDARY_CONSTRUCTOR)
val DECLARATIONS = TokenSet.create(KtNodeTypes.PROPERTY, KtNodeTypes.FUN, KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION,
                                   KtNodeTypes.ENUM_ENTRY, KtNodeTypes.SECONDARY_CONSTRUCTOR, KtNodeTypes.CLASS_INITIALIZER)
val TYPE_COLON_ELEMENTS = TokenSet.create(KtNodeTypes.PROPERTY, KtNodeTypes.FUN, KtNodeTypes.VALUE_PARAMETER,
                                          KtNodeTypes.DESTRUCTURING_DECLARATION_ENTRY, KtNodeTypes.FUNCTION_LITERAL)
fun SpacingBuilder.beforeInside(element: IElementType, tokenSet: TokenSet, spacingFun: SpacingBuilder.RuleBuilder.() -> Unit) {
    tokenSet.types.forEach { inType -> beforeInside(element, inType).spacingFun() }
}

fun SpacingBuilder.afterInside(element: IElementType, tokenSet: TokenSet, spacingFun: SpacingBuilder.RuleBuilder.() -> Unit) {
    tokenSet.types.forEach { inType -> afterInside(element, inType).spacingFun() }
}

fun SpacingBuilder.RuleBuilder.spacesNoLineBreak(spaces: Int): SpacingBuilder? =
    spacing(spaces, spaces, 0, false, 0)

fun createSpacingBuilder(settings: CodeStyleSettings, builderUtil: KotlinSpacingBuilderUtil): KotlinSpacingBuilder {
    val kotlinCommonSettings = settings.kotlinCommonSettings
    val kotlinCustomSettings = settings.kotlinCustomSettings
    return rules(kotlinCommonSettings, builderUtil) {
      simple {
        before(KtNodeTypes.FILE_ANNOTATION_LIST).lineBreakInCode()
        between(KtNodeTypes.IMPORT_DIRECTIVE, KtNodeTypes.IMPORT_DIRECTIVE).lineBreakInCode()
        after(KtNodeTypes.IMPORT_LIST).blankLines(1)
      }

      custom {
        fun commentSpacing(minSpaces: Int): Spacing {
          if (kotlinCommonSettings.KEEP_FIRST_COLUMN_COMMENT) {
            return Spacing.createKeepingFirstColumnSpacing(
              minSpaces,
              Int.MAX_VALUE,
              kotlinCommonSettings.KEEP_LINE_BREAKS,
              kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE
            )
          }
          return Spacing.createSpacing(
            minSpaces,
            Int.MAX_VALUE,
            0,
            kotlinCommonSettings.KEEP_LINE_BREAKS,
            kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE
          )
        }

        // Several line comments happened to be generated in one line
        inPosition(parent = null, left = KtTokens.EOL_COMMENT, right = KtTokens.EOL_COMMENT).customRule { _, _, right ->
          val nodeBeforeRight = right.requireNode().treePrev
          if (nodeBeforeRight is PsiWhiteSpace && !nodeBeforeRight.textContains('\n')) {
            createSpacing(0, minLineFeeds = 1)
          }
          else {
            null
          }
        }

        inPosition(right = KtTokens.BLOCK_COMMENT).spacing(
          Spacing.createSpacing(
            0,
            Int.MAX_VALUE,
            0,
            kotlinCommonSettings.KEEP_LINE_BREAKS,
            kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE,
          )
        )

        inPosition(right = KtTokens.EOL_COMMENT).spacing(commentSpacing(1))
        inPosition(parent = KtNodeTypes.FUNCTION_LITERAL, right = KtNodeTypes.BLOCK).customRule { _, _, right ->
          when (right.node?.children()?.firstOrNull()?.elementType) {
            KtTokens.BLOCK_COMMENT -> commentSpacing(0)
            KtTokens.EOL_COMMENT -> commentSpacing(1)
            else -> null
          }
        }
      }

      simple {
        after(KtNodeTypes.FILE_ANNOTATION_LIST).blankLines(1)
        after(KtNodeTypes.PACKAGE_DIRECTIVE).blankLines(1)
      }

      custom {
        inPosition(leftSet = DECLARATIONS, rightSet = DECLARATIONS).customRule(fun(
          _: ASTBlock,
          _: ASTBlock,
          right: ASTBlock
        ): Spacing? {
          val node = right.node ?: return null
          val elementStart = node.startOfDeclaration() ?: return null
          return if (StringUtil.containsLineBreak(
              node.text.subSequence(0, elementStart.startOffset - node.startOffset).trimStart()
            )
          )
            createSpacing(0,
                          minLineFeeds = 1 + kotlinCustomSettings.BLANK_LINES_BEFORE_DECLARATION_WITH_COMMENT_OR_ANNOTATION_ON_SEPARATE_LINE)
          else
            null
        })

        inPosition(left = KtNodeTypes.CLASS, right = KtNodeTypes.CLASS).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.CLASS, right = KtNodeTypes.OBJECT_DECLARATION).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.OBJECT_DECLARATION, right = KtNodeTypes.OBJECT_DECLARATION).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.OBJECT_DECLARATION, right = KtNodeTypes.CLASS).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.FUN, right = KtNodeTypes.FUN).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.PROPERTY, right = KtNodeTypes.FUN).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.FUN, right = KtNodeTypes.PROPERTY).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.SECONDARY_CONSTRUCTOR, right = KtNodeTypes.SECONDARY_CONSTRUCTOR).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.TYPEALIAS, right = KtNodeTypes.TYPEALIAS).emptyLinesIfLineBreakInLeft(1)

        // Case left for alternative constructors
        inPosition(left = KtNodeTypes.FUN, right = KtNodeTypes.CLASS).emptyLinesIfLineBreakInLeft(1)
        inPosition(left = KtNodeTypes.ENUM_ENTRY, right = KtNodeTypes.ENUM_ENTRY).emptyLinesIfLineBreakInLeft(
          emptyLines = 0, numberOfLineFeedsOtherwise = 0, numSpacesOtherwise = 1
        )

        inPosition(parent = KtNodeTypes.CLASS_BODY, left = KtTokens.SEMICOLON).customRule { parent, _, right ->
          val klass = parent.requireNode().treeParent.psi as? KtClass ?: return@customRule null
          if (klass.isEnum() && right.requireNode().elementType in DECLARATIONS) {
            createSpacing(0, minLineFeeds = 2, keepBlankLines = kotlinCommonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
          }
          else null
        }

        inPosition(parent = KtNodeTypes.CLASS_BODY, left = KtTokens.LBRACE).customRule { parent, left, right ->
          if (right.requireNode().elementType == KtTokens.RBRACE) {
            return@customRule createSpacing(0)
          }
          val classBody = parent.requireNode().psi as KtClassBody
          val parentPsi = classBody.parent as? KtClassOrObject ?: return@customRule null
          if (kotlinCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER == 0 || parentPsi.isObjectLiteral()) {
            null
          }
          else {
            val minLineFeeds = if (right.requireNode().elementType == KtNodeTypes.FUN || right.requireNode().elementType == KtNodeTypes.PROPERTY)
              kotlinCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER + 1
            else
              0

            builderUtil.createLineFeedDependentSpacing(
              1,
              1,
              minLineFeeds,
              commonCodeStyleSettings.KEEP_LINE_BREAKS,
              commonCodeStyleSettings.KEEP_BLANK_LINES_IN_DECLARATIONS,
              TextRange(parentPsi.textRange.startOffset, left.requireNode().psi.textRange.startOffset),
              DependentSpacingRule(DependentSpacingRule.Trigger.HAS_LINE_FEEDS)
                .registerData(
                  DependentSpacingRule.Anchor.MIN_LINE_FEEDS,
                  kotlinCommonSettings.BLANK_LINES_AFTER_CLASS_HEADER + 1
                )
            )
          }
        }

        val parameterWithDocCommentRule = { _: ASTBlock, _: ASTBlock, right: ASTBlock ->
          if (right.requireNode().firstChildNode.elementType == KtTokens.DOC_COMMENT) {
            createSpacing(0, minLineFeeds = 1, keepLineBreaks = true,
                          keepBlankLines = kotlinCommonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
          }
          else {
            null
          }
        }
        inPosition(parent = KtNodeTypes.VALUE_PARAMETER_LIST, right = KtNodeTypes.VALUE_PARAMETER).customRule(parameterWithDocCommentRule)

        inPosition(parent = KtNodeTypes.PROPERTY, right = KtNodeTypes.PROPERTY_ACCESSOR).customRule { parent, _, _ ->
          val startNode = parent.requireNode().let { it.startOfDeclaration() ?: it }
          Spacing.createDependentLFSpacing(
            1, 1,
            TextRange(startNode.startOffset, parent.textRange.endOffset),
            false, 0
          )
        }

        inPosition(parent = KtNodeTypes.VALUE_ARGUMENT_LIST, left = KtTokens.LPAR).customRule { parent, _, _ ->
          when {
            kotlinCommonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE && needWrapArgumentList(
              parent.requireNode().psi) -> {
              Spacing.createDependentLFSpacing(
                0, 0,
                excludeLambdasAndObjects(parent),
                commonCodeStyleSettings.KEEP_LINE_BREAKS,
                commonCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE
              )
            }

            kotlinCustomSettings.ALLOW_TRAILING_COMMA -> null
            else -> createSpacing(0)
          }
        }

        inPosition(parent = KtNodeTypes.VALUE_ARGUMENT_LIST, right = KtTokens.RPAR).customRule { parent, left, _ ->
          when {
            kotlinCommonSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE -> {
              Spacing.createDependentLFSpacing(
                0, 0,
                excludeLambdasAndObjects(parent),
                commonCodeStyleSettings.KEEP_LINE_BREAKS,
                commonCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE
              )
            }

            kotlinCustomSettings.ALLOW_TRAILING_COMMA -> null
            left.requireNode().elementType == KtTokens.COMMA -> /* incomplete call being edited */ createSpacing(1)
            else -> createSpacing(0)
          }
        }

        inPosition(left = KtNodeTypes.CONDITION, right = KtTokens.RPAR).customRule { _, left, _ ->
          if (kotlinCustomSettings.IF_RPAREN_ON_NEW_LINE) {
            Spacing.createDependentLFSpacing(
              0, 0,
              excludeLambdasAndObjects(left),
              commonCodeStyleSettings.KEEP_LINE_BREAKS,
              commonCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE
            )
          }
          else {
            createSpacing(0)
          }
        }

        inPosition(left = KtNodeTypes.VALUE_PARAMETER, right = KtTokens.COMMA).customRule { _, left, _ ->
          if (left.node?.lastChildNode?.elementType === KtTokens.EOL_COMMENT)
            createSpacing(0, minLineFeeds = 1)
          else
            null
        }

        inPosition(parent = KtNodeTypes.LONG_STRING_TEMPLATE_ENTRY, right = KtTokens.LONG_TEMPLATE_ENTRY_END).lineBreakIfLineBreakInParent(
          0)
        inPosition(parent = KtNodeTypes.LONG_STRING_TEMPLATE_ENTRY, left = KtTokens.LONG_TEMPLATE_ENTRY_START).lineBreakIfLineBreakInParent(
          0)
      }

      simple {
        // ============ Line breaks ==============
        before(KtTokens.DOC_COMMENT).lineBreakInCode()
        between(KtNodeTypes.PROPERTY, KtNodeTypes.PROPERTY).lineBreakInCode()

        // CLASS - CLASS, CLASS - OBJECT_DECLARATION are exception
        between(KtNodeTypes.CLASS, DECLARATIONS).blankLines(1)

        // FUN - FUN, FUN - PROPERTY, FUN - CLASS are exceptions
        between(KtNodeTypes.FUN, DECLARATIONS).blankLines(1)

        // PROPERTY - PROPERTY, PROPERTY - FUN are exceptions
        between(KtNodeTypes.PROPERTY, DECLARATIONS).blankLines(1)

        // OBJECT_DECLARATION - OBJECT_DECLARATION, CLASS - OBJECT_DECLARATION are exception
        between(KtNodeTypes.OBJECT_DECLARATION, DECLARATIONS).blankLines(1)
        between(KtNodeTypes.SECONDARY_CONSTRUCTOR, DECLARATIONS).blankLines(1)
        between(KtNodeTypes.CLASS_INITIALIZER, DECLARATIONS).blankLines(1)

        // TYPEALIAS - TYPEALIAS is an exception
        between(KtNodeTypes.TYPEALIAS, DECLARATIONS).blankLines(1)
        before(KtNodeTypes.TYPEALIAS).lineBreakInCode()

        // ENUM_ENTRY - ENUM_ENTRY is exception
        between(KtNodeTypes.ENUM_ENTRY, DECLARATIONS).blankLines(1)

        between(KtNodeTypes.ENUM_ENTRY, KtTokens.SEMICOLON).spaces(0)

        between(KtTokens.COMMA, KtTokens.SEMICOLON).lineBreakInCodeIf(kotlinCustomSettings.ALLOW_TRAILING_COMMA)

        beforeInside(KtNodeTypes.FUN, TokenSet.create(KtNodeTypes.BODY, KtNodeTypes.CLASS_BODY)).lineBreakInCode()
        beforeInside(KtNodeTypes.SECONDARY_CONSTRUCTOR, TokenSet.create(KtNodeTypes.BODY, KtNodeTypes.CLASS_BODY)).lineBreakInCode()
        beforeInside(KtNodeTypes.CLASS, TokenSet.create(KtNodeTypes.BODY, KtNodeTypes.CLASS_BODY)).lineBreakInCode()
        beforeInside(KtNodeTypes.OBJECT_DECLARATION, TokenSet.create(KtNodeTypes.BODY, KtNodeTypes.CLASS_BODY)).lineBreakInCode()
        beforeInside(KtNodeTypes.PROPERTY, KtNodeTypes.WHEN).spaces(0)
        beforeInside(KtNodeTypes.PROPERTY, KtNodeTypes.LABELED_EXPRESSION).spacesNoLineBreak(1)
        before(KtNodeTypes.PROPERTY).lineBreakInCode()

        after(KtTokens.DOC_COMMENT).lineBreakInCode()

        // =============== Spacing ================
        aroundInside(KtTokens.AND, KtNodeTypes.INTERSECTION_TYPE).spacesNoLineBreak(1)

        between(KtTokens.EOL_COMMENT, KtTokens.COMMA).lineBreakInCode()
        before(KtTokens.COMMA).spacesNoLineBreak(if (kotlinCommonSettings.SPACE_BEFORE_COMMA) 1 else 0)
        after(KtTokens.COMMA).spaceIf(kotlinCommonSettings.SPACE_AFTER_COMMA)

        val spacesAroundAssignment = if (kotlinCommonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS) 1 else 0
        beforeInside(KtTokens.EQ, KtNodeTypes.PROPERTY).spacesNoLineBreak(spacesAroundAssignment)
        beforeInside(KtTokens.EQ, KtNodeTypes.FUN).spacing(spacesAroundAssignment, spacesAroundAssignment, 0, false, 0)

        around(
          TokenSet.create(KtTokens.EQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.PERCEQ)
        ).spaceIf(kotlinCommonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
        around(TokenSet.create(KtTokens.ANDAND, KtTokens.OROR)).spaceIf(kotlinCommonSettings.SPACE_AROUND_LOGICAL_OPERATORS)
        around(TokenSet.create(KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ)).spaceIf(
          kotlinCommonSettings.SPACE_AROUND_EQUALITY_OPERATORS)
        aroundInside(
          TokenSet.create(KtTokens.LT, KtTokens.GT, KtTokens.LTEQ, KtTokens.GTEQ), KtNodeTypes.BINARY_EXPRESSION
        ).spaceIf(kotlinCommonSettings.SPACE_AROUND_RELATIONAL_OPERATORS)
        aroundInside(TokenSet.create(KtTokens.PLUS, KtTokens.MINUS), KtNodeTypes.BINARY_EXPRESSION).spaceIf(
          kotlinCommonSettings.SPACE_AROUND_ADDITIVE_OPERATORS)
        aroundInside(
          TokenSet.create(KtTokens.MUL, KtTokens.DIV, KtTokens.PERC), KtNodeTypes.BINARY_EXPRESSION
        ).spaceIf(kotlinCommonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
        around(
          TokenSet.create(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS, KtTokens.EXCLEXCL, KtTokens.MINUS, KtTokens.PLUS, KtTokens.EXCL)
        ).spaceIf(kotlinCommonSettings.SPACE_AROUND_UNARY_OPERATOR)

        val spacesAroundElvis = if (kotlinCustomSettings.SPACE_AROUND_ELVIS) 1 else 0
        before(KtTokens.ELVIS).spaceIf(kotlinCustomSettings.SPACE_AROUND_ELVIS)
        after(KtTokens.ELVIS).spacesNoLineBreak(spacesAroundElvis)

        around(KtTokens.RANGE).spaceIf(kotlinCustomSettings.SPACE_AROUND_RANGE)
        around(KtTokens.RANGE_UNTIL).spaceIf(kotlinCustomSettings.SPACE_AROUND_RANGE)

        after(KtNodeTypes.MODIFIER_LIST).spaces(1)

        beforeInside(KtTokens.IDENTIFIER, KtNodeTypes.CLASS).spaces(1)
        beforeInside(KtTokens.IDENTIFIER, KtNodeTypes.OBJECT_DECLARATION).spaces(1)

        after(KtTokens.TYPE_ALIAS_KEYWORD).spaces(1)

        after(KtTokens.VAL_KEYWORD).spaces(1)
        after(KtTokens.VAR_KEYWORD).spaces(1)
        betweenInside(KtNodeTypes.TYPE_PARAMETER_LIST, KtTokens.IDENTIFIER, KtNodeTypes.PROPERTY).spaces(1)
        betweenInside(KtNodeTypes.TYPE_REFERENCE, KtTokens.DOT, KtNodeTypes.PROPERTY).spacing(0, 0, 0, false, 0)
        betweenInside(KtTokens.DOT, KtTokens.IDENTIFIER, KtNodeTypes.PROPERTY).spacing(0, 0, 0, false, 0)

        betweenInside(KtTokens.RETURN_KEYWORD, KtNodeTypes.LABEL_QUALIFIER, KtNodeTypes.RETURN).spaces(0)
        afterInside(KtTokens.RETURN_KEYWORD, KtNodeTypes.RETURN).spaces(1)
        afterInside(KtNodeTypes.LABEL_QUALIFIER, KtNodeTypes.RETURN).spaces(1)
        betweenInside(KtNodeTypes.LABEL_QUALIFIER, KtTokens.EOL_COMMENT, KtNodeTypes.LABELED_EXPRESSION).spacing(
          0, Int.MAX_VALUE, 0, true, kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE
        )
        betweenInside(KtNodeTypes.LABEL_QUALIFIER, KtTokens.BLOCK_COMMENT, KtNodeTypes.LABELED_EXPRESSION).spacing(
          0, Int.MAX_VALUE, 0, true, kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE
        )
        betweenInside(KtNodeTypes.LABEL_QUALIFIER, KtNodeTypes.LAMBDA_EXPRESSION, KtNodeTypes.LABELED_EXPRESSION).spaces(0)
        afterInside(KtNodeTypes.LABEL_QUALIFIER, KtNodeTypes.LABELED_EXPRESSION).spaces(1)

        betweenInside(KtTokens.FUN_KEYWORD, KtNodeTypes.VALUE_PARAMETER_LIST, KtNodeTypes.FUN).spacing(0, 0, 0, false, 0)
        after(KtTokens.FUN_KEYWORD).spaces(1)
        betweenInside(KtNodeTypes.TYPE_PARAMETER_LIST, KtNodeTypes.TYPE_REFERENCE, KtNodeTypes.FUN).spaces(1)
        betweenInside(KtNodeTypes.TYPE_PARAMETER_LIST, KtTokens.IDENTIFIER, KtNodeTypes.FUN).spaces(1)
        betweenInside(KtNodeTypes.TYPE_REFERENCE, KtTokens.DOT, KtNodeTypes.FUN).spacing(0, 0, 0, false, 0)
        betweenInside(KtTokens.DOT, KtTokens.IDENTIFIER, KtNodeTypes.FUN).spacing(0, 0, 0, false, 0)
        afterInside(KtTokens.IDENTIFIER, KtNodeTypes.FUN).spacing(0, 0, 0, false, 0)
        aroundInside(KtTokens.DOT, KtNodeTypes.USER_TYPE).spaces(0)

        around(KtTokens.AS_KEYWORD).spaces(1)
        around(KtTokens.AS_SAFE).spaces(1)
        around(KtTokens.IS_KEYWORD).spaces(1)
        around(KtTokens.NOT_IS).spaces(1)
        around(KtTokens.IN_KEYWORD).spaces(1)
        around(KtTokens.NOT_IN).spaces(1)
        aroundInside(KtTokens.IDENTIFIER, KtNodeTypes.BINARY_EXPRESSION).spaces(1)

        after(KtTokens.THROW_KEYWORD).spacesNoLineBreak(1)

        // before LPAR in constructor(): this() {}
        after(KtNodeTypes.CONSTRUCTOR_DELEGATION_REFERENCE).spacing(0, 0, 0, false, 0)

        // class A() - no space before LPAR of PRIMARY_CONSTRUCTOR
        // class A private() - one space before modifier
        custom {
          inPosition(right = KtNodeTypes.PRIMARY_CONSTRUCTOR).customRule { _, _, r ->
            val spacesCount = if (r.requireNode().findLeafElementAt(0)?.elementType != KtTokens.LPAR) 1 else 0
            createSpacing(spacesCount, minLineFeeds = 0, keepLineBreaks = true, keepBlankLines = 0)
          }
        }

        afterInside(KtTokens.CONSTRUCTOR_KEYWORD, KtNodeTypes.PRIMARY_CONSTRUCTOR).spaces(0)
        betweenInside(KtTokens.IDENTIFIER, KtNodeTypes.TYPE_PARAMETER_LIST, KtNodeTypes.CLASS).spaces(0)

        beforeInside(KtTokens.DOT, KtNodeTypes.DOT_QUALIFIED_EXPRESSION).spaces(0)
        afterInside(KtTokens.DOT, KtNodeTypes.DOT_QUALIFIED_EXPRESSION).spacesNoLineBreak(0)
        beforeInside(KtTokens.SAFE_ACCESS, KtNodeTypes.SAFE_ACCESS_EXPRESSION).spaces(0)
        afterInside(KtTokens.SAFE_ACCESS, KtNodeTypes.SAFE_ACCESS_EXPRESSION).spacesNoLineBreak(0)

        between(MODIFIERS_LIST_ENTRIES, MODIFIERS_LIST_ENTRIES).spaces(1)

        after(KtTokens.LBRACKET).spaces(0)
        before(KtTokens.RBRACKET).spaces(0)

        afterInside(KtTokens.LPAR, KtNodeTypes.VALUE_PARAMETER_LIST).spaces(0, kotlinCommonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
        beforeInside(KtTokens.RPAR, KtNodeTypes.VALUE_PARAMETER_LIST).spaces(0, kotlinCommonSettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE)
        afterInside(KtTokens.LT, KtNodeTypes.TYPE_PARAMETER_LIST).spaces(0)
        beforeInside(KtTokens.GT, KtNodeTypes.TYPE_PARAMETER_LIST).spaces(0)
        afterInside(KtTokens.LT, KtNodeTypes.TYPE_ARGUMENT_LIST).spaces(0)
        beforeInside(KtTokens.GT, KtNodeTypes.TYPE_ARGUMENT_LIST).spaces(0)
        before(KtNodeTypes.TYPE_ARGUMENT_LIST).spaces(0)

        after(KtTokens.LPAR).spaces(0)
        before(KtTokens.RPAR).spaces(0)

        betweenInside(KtTokens.FOR_KEYWORD, KtTokens.LPAR, KtNodeTypes.FOR).spaceIf(kotlinCommonSettings.SPACE_BEFORE_FOR_PARENTHESES)
        betweenInside(KtTokens.IF_KEYWORD, KtTokens.LPAR, KtNodeTypes.IF).spaceIf(kotlinCommonSettings.SPACE_BEFORE_IF_PARENTHESES)
        betweenInside(KtTokens.WHILE_KEYWORD, KtTokens.LPAR, KtNodeTypes.WHILE).spaceIf(kotlinCommonSettings.SPACE_BEFORE_WHILE_PARENTHESES)
        betweenInside(KtTokens.WHILE_KEYWORD, KtTokens.LPAR, KtNodeTypes.DO_WHILE).spaceIf(
          kotlinCommonSettings.SPACE_BEFORE_WHILE_PARENTHESES)
        betweenInside(KtTokens.WHEN_KEYWORD, KtTokens.LPAR, KtNodeTypes.WHEN).spaceIf(kotlinCustomSettings.SPACE_BEFORE_WHEN_PARENTHESES)
        betweenInside(KtTokens.CATCH_KEYWORD, KtNodeTypes.VALUE_PARAMETER_LIST, KtNodeTypes.CATCH).spaceIf(
          kotlinCommonSettings.SPACE_BEFORE_CATCH_PARENTHESES)

        betweenInside(KtTokens.LPAR, KtNodeTypes.VALUE_PARAMETER, KtNodeTypes.FOR).spaces(0)
        betweenInside(KtTokens.LPAR, KtNodeTypes.DESTRUCTURING_DECLARATION, KtNodeTypes.FOR).spaces(0)
        betweenInside(KtNodeTypes.LOOP_RANGE, KtTokens.RPAR, KtNodeTypes.FOR).spaces(0)

        afterInside(KtNodeTypes.ANNOTATION_ENTRY, KtNodeTypes.ANNOTATED_EXPRESSION).spaces(1)

        before(KtTokens.SEMICOLON).spaces(0)

        beforeInside(KtNodeTypes.INITIALIZER_LIST, KtNodeTypes.ENUM_ENTRY).spaces(0)

        beforeInside(KtTokens.QUEST, KtNodeTypes.NULLABLE_TYPE).spaces(0)

        beforeInside(KtTokens.COLON, TYPE_COLON_ELEMENTS) { spaceIf(kotlinCustomSettings.SPACE_BEFORE_TYPE_COLON) }
        afterInside(KtTokens.COLON, TYPE_COLON_ELEMENTS) { spaceIf(kotlinCustomSettings.SPACE_AFTER_TYPE_COLON) }

        afterInside(KtTokens.COLON, EXTEND_COLON_ELEMENTS) { spaceIf(kotlinCustomSettings.SPACE_AFTER_EXTEND_COLON) }

        beforeInside(KtTokens.ARROW, KtNodeTypes.FUNCTION_LITERAL).spaceIf(kotlinCustomSettings.SPACE_BEFORE_LAMBDA_ARROW)

        aroundInside(KtTokens.ARROW, KtNodeTypes.FUNCTION_TYPE).spaceIf(kotlinCustomSettings.SPACE_AROUND_FUNCTION_TYPE_ARROW)

        before(KtNodeTypes.VALUE_ARGUMENT_LIST).spaces(0)
        between(KtNodeTypes.VALUE_ARGUMENT_LIST, KtNodeTypes.LAMBDA_ARGUMENT).spaces(1)
        betweenInside(KtNodeTypes.REFERENCE_EXPRESSION, KtNodeTypes.LAMBDA_ARGUMENT, KtNodeTypes.CALL_EXPRESSION).spaces(1)
        betweenInside(KtNodeTypes.TYPE_ARGUMENT_LIST, KtNodeTypes.LAMBDA_ARGUMENT, KtNodeTypes.CALL_EXPRESSION).spaces(1)
        betweenInside(KtNodeTypes.STRING_TEMPLATE, KtNodeTypes.LAMBDA_ARGUMENT, KtNodeTypes.CALL_EXPRESSION).spaces(1)

        around(KtTokens.COLONCOLON).spaces(0)

        around(KtTokens.BY_KEYWORD).spaces(1)
        betweenInside(KtTokens.IDENTIFIER, KtNodeTypes.PROPERTY_DELEGATE, KtNodeTypes.PROPERTY).spaces(1)
        betweenInside(KtNodeTypes.TYPE_REFERENCE, KtNodeTypes.PROPERTY_DELEGATE, KtNodeTypes.PROPERTY).spaces(1)

        before(KtNodeTypes.INDICES).spaces(0)
        before(KtTokens.WHERE_KEYWORD).spaces(1)

        afterInside(KtTokens.GET_KEYWORD, KtNodeTypes.PROPERTY_ACCESSOR).spaces(0)
        afterInside(KtTokens.SET_KEYWORD, KtNodeTypes.PROPERTY_ACCESSOR).spaces(0)
      }
      custom {

        fun KotlinSpacingBuilder.CustomSpacingBuilder.ruleForKeywordOnNewLine(
          shouldBeOnNewLine: Boolean,
          keyword: IElementType,
          parent: IElementType,
          afterBlockFilter: (wordParent: ASTNode, block: ASTNode) -> Boolean = { _, _ -> true }
        ) {
          if (shouldBeOnNewLine) {
            inPosition(parent = parent, right = keyword)
              .lineBreakIfLineBreakInParent(numSpacesOtherwise = 1, allowBlankLines = false)
          }
          else {
            inPosition(parent = parent, right = keyword).customRule { _, _, right ->

              val previousLeaf = builderUtil.getPreviousNonWhitespaceLeaf(right.requireNode())
              val leftBlock = if (
                previousLeaf != null &&
                previousLeaf.elementType == KtTokens.RBRACE &&
                previousLeaf.treeParent?.elementType == KtNodeTypes.BLOCK
              ) {
                previousLeaf.treeParent!!
              }
              else null

              val removeLineBreaks = leftBlock != null && afterBlockFilter(right.node?.treeParent!!, leftBlock)
              createSpacing(1, minLineFeeds = 0, keepLineBreaks = !removeLineBreaks, keepBlankLines = 0)
            }
          }
        }

        ruleForKeywordOnNewLine(kotlinCommonSettings.ELSE_ON_NEW_LINE, keyword = KtTokens.ELSE_KEYWORD,
                                parent = KtNodeTypes.IF) { keywordParent, block ->
          block.treeParent?.elementType == KtNodeTypes.THEN && block.treeParent?.treeParent == keywordParent
        }
        ruleForKeywordOnNewLine(
          kotlinCommonSettings.WHILE_ON_NEW_LINE,
          keyword = KtTokens.WHILE_KEYWORD,
          parent = KtNodeTypes.DO_WHILE
        ) { keywordParent, block ->
          block.treeParent?.elementType == KtNodeTypes.BODY && block.treeParent?.treeParent == keywordParent
        }
        ruleForKeywordOnNewLine(kotlinCommonSettings.CATCH_ON_NEW_LINE, keyword = KtNodeTypes.CATCH, parent = KtNodeTypes.TRY)
        ruleForKeywordOnNewLine(kotlinCommonSettings.FINALLY_ON_NEW_LINE, keyword = KtNodeTypes.FINALLY, parent = KtNodeTypes.TRY)


        fun spacingForLeftBrace(block: ASTNode?, blockType: IElementType = KtNodeTypes.BLOCK): Spacing {
          if (block != null && block.elementType == blockType) {
            val leftBrace = block.findChildByType(KtTokens.LBRACE)
            if (leftBrace != null) {
              val previousLeaf = builderUtil.getPreviousNonWhitespaceLeaf(leftBrace)
              val isAfterEolComment = previousLeaf != null && (previousLeaf.elementType == KtTokens.EOL_COMMENT)
              val keepLineBreaks = kotlinCustomSettings.LBRACE_ON_NEXT_LINE || isAfterEolComment
              val minimumLF = if (kotlinCustomSettings.LBRACE_ON_NEXT_LINE) 1 else 0
              return createSpacing(1, minLineFeeds = minimumLF, keepLineBreaks = keepLineBreaks, keepBlankLines = 0)
            }
          }
          return createSpacing(1)
        }

        fun leftBraceRule(blockType: IElementType = KtNodeTypes.BLOCK) = { _: ASTBlock, _: ASTBlock, right: ASTBlock ->
          spacingForLeftBrace(right.node, blockType)
        }

        val leftBraceRuleIfBlockIsWrapped = { _: ASTBlock, _: ASTBlock, right: ASTBlock ->
          spacingForLeftBrace(right.requireNode().firstChildNode)
        }

        // Add space after a semicolon if there is another child at the same line
        inPosition(left = KtTokens.SEMICOLON).customRule { _, left, _ ->
          val nodeAfterLeft = left.requireNode().treeNext
          if (nodeAfterLeft is PsiWhiteSpace && !nodeAfterLeft.textContains('\n')) {
            createSpacing(1)
          }
          else {
            null
          }
        }

        inPosition(parent = KtNodeTypes.IF, right = KtNodeTypes.THEN).customRule(leftBraceRuleIfBlockIsWrapped)
        inPosition(parent = KtNodeTypes.IF, right = KtNodeTypes.ELSE).customRule(leftBraceRuleIfBlockIsWrapped)

        inPosition(parent = KtNodeTypes.FOR, right = KtNodeTypes.BODY).customRule(leftBraceRuleIfBlockIsWrapped)
        inPosition(parent = KtNodeTypes.WHILE, right = KtNodeTypes.BODY).customRule(leftBraceRuleIfBlockIsWrapped)
        inPosition(parent = KtNodeTypes.DO_WHILE, right = KtNodeTypes.BODY).customRule(leftBraceRuleIfBlockIsWrapped)

        inPosition(parent = KtNodeTypes.TRY, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.CATCH, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.FINALLY, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())

        inPosition(parent = KtNodeTypes.FUN, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.SECONDARY_CONSTRUCTOR, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.CLASS_INITIALIZER, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.PROPERTY_ACCESSOR, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())

        inPosition(right = KtNodeTypes.CLASS_BODY).customRule(leftBraceRule(blockType = KtNodeTypes.CLASS_BODY))

        inPosition(left = KtNodeTypes.WHEN_ENTRY, right = KtNodeTypes.WHEN_ENTRY).customRule { _, left, right ->
          val blankLines = kotlinCustomSettings.BLANK_LINES_AROUND_BLOCK_WHEN_BRANCHES
          if (blankLines != 0) {
            val leftEntry = left.requireNode().psi as KtWhenEntry
            val rightEntry = right.requireNode().psi as KtWhenEntry
            if (leftEntry.expression is KtBlockExpression || rightEntry.expression is KtBlockExpression) {
              return@customRule createSpacing(0, minLineFeeds = blankLines + 1)
            }
          }

          if (kotlinCustomSettings.LINE_BREAK_AFTER_MULTILINE_WHEN_ENTRY) {
            builderUtil.createLineFeedDependentSpacing(
              minSpaces = 0,
              maxSpaces = 0,
              minimumLineFeeds = 1,
              keepLineBreaks = commonCodeStyleSettings.KEEP_LINE_BREAKS,
              keepBlankLines = commonCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE,
              dependency = left.textRange,
              rule = DependentSpacingRule(DependentSpacingRule.Trigger.HAS_LINE_FEEDS).registerData(
                DependentSpacingRule.Anchor.MIN_LINE_FEEDS,
                2,
              ),
            )
          }
          else {
            createSpacing(0, minLineFeeds = 1)
          }
        }

        inPosition(parent = KtNodeTypes.WHEN_ENTRY, right = KtNodeTypes.BLOCK).customRule(leftBraceRule())
        inPosition(parent = KtNodeTypes.WHEN, right = KtTokens.LBRACE).customRule { parent, _, _ ->
          spacingForLeftBrace(block = parent.requireNode(), blockType = KtNodeTypes.WHEN)
        }

        inPosition(left = KtTokens.LBRACE, right = KtNodeTypes.WHEN_ENTRY).customRule { _, _, _ ->
          createSpacing(0, minLineFeeds = 1)
        }

        val spacesInSimpleFunction = if (kotlinCustomSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD) 1 else 0
        inPosition(
          parent = KtNodeTypes.FUNCTION_LITERAL,
          left = KtTokens.LBRACE,
          right = KtNodeTypes.BLOCK
        ).lineBreakIfLineBreakInParent(numSpacesOtherwise = spacesInSimpleFunction)

        inPosition(
          parent = KtNodeTypes.FUNCTION_LITERAL,
          left = KtTokens.ARROW,
          right = KtNodeTypes.BLOCK
        ).lineBreakIfLineBreakInParent(numSpacesOtherwise = 1)

        inPosition(
          parent = KtNodeTypes.FUNCTION_LITERAL,
          left = KtTokens.LBRACE,
          right = KtTokens.RBRACE
        ).spacing(createSpacing(minSpaces = 0, maxSpaces = 1))

        inPosition(
          parent = KtNodeTypes.FUNCTION_LITERAL,
          right = KtTokens.RBRACE
        ).lineBreakIfLineBreakInParent(numSpacesOtherwise = spacesInSimpleFunction)

        inPosition(
          parent = KtNodeTypes.FUNCTION_LITERAL,
          left = KtTokens.LBRACE
        ).customRule { _, _, right ->
          val rightNode = right.requireNode()
          val rightType = rightNode.elementType
          if (rightType == KtNodeTypes.VALUE_PARAMETER_LIST) {
            createSpacing(spacesInSimpleFunction, keepLineBreaks = false)
          }
          else {
            createSpacing(spacesInSimpleFunction)
          }
        }

        inPosition(parent = KtNodeTypes.CLASS_BODY, right = KtTokens.RBRACE).customRule { parent, _, _ ->
          kotlinCommonSettings.createSpaceBeforeRBrace(1, parent.textRange)
        }

        inPosition(parent = KtNodeTypes.BLOCK, right = KtTokens.RBRACE).customRule(
          fun(block: ASTBlock, left: ASTBlock, _: ASTBlock): Spacing? {
            val psiElement = block.requireNode().treeParent.psi

            val empty = left.requireNode().elementType == KtTokens.LBRACE

            when (psiElement) {
              is KtDeclarationWithBody -> if (psiElement.name != null && !empty) return null
              is KtWhenEntry, is KtClassInitializer -> if (!empty) return null
              else -> return null
            }

            val spaces = if (empty) 0 else spacesInSimpleFunction
            return kotlinCommonSettings.createSpaceBeforeRBrace(spaces, psiElement.textRangeWithoutComments)
          })

        inPosition(parent = KtNodeTypes.BLOCK, left = KtTokens.LBRACE).customRule { parent, _, _ ->
          val psiElement = parent.requireNode().treeParent.psi
          val funNode = psiElement as? KtFunction ?: return@customRule null

          if (funNode.name != null) return@customRule null

          // Empty block is covered in above rule
          Spacing.createDependentLFSpacing(
            spacesInSimpleFunction, spacesInSimpleFunction, funNode.textRangeWithoutComments,
            kotlinCommonSettings.KEEP_LINE_BREAKS,
            kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE
          )
        }

        inPosition(parentSet = EXTEND_COLON_ELEMENTS, left = KtNodeTypes.PRIMARY_CONSTRUCTOR,
                   right = KtTokens.COLON).customRule { _, left, _ ->
          val primaryConstructor = left.requireNode().psi as KtPrimaryConstructor
          val rightParenthesis = primaryConstructor.valueParameterList?.rightParenthesis
          val prevSibling = rightParenthesis?.prevSibling
          val spaces = if (kotlinCustomSettings.SPACE_BEFORE_EXTEND_COLON) 1 else 0
          // TODO This should use DependentSpacingRule, but it doesn't set keepLineBreaks to false if max LFs is 0
          if ((prevSibling as? PsiWhiteSpace)?.textContains('\n') == true || kotlinCommonSettings
              .METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE
          ) {
            createSpacing(spaces, keepLineBreaks = false)
          }
          else {
            createSpacing(spaces)
          }
        }

        inPosition(
          parent = KtNodeTypes.CLASS_BODY,
          left = KtTokens.LBRACE,
          right = KtNodeTypes.ENUM_ENTRY
        ).lineBreakIfLineBreakInParent(numSpacesOtherwise = 1)
      }

      simple {
        afterInside(KtTokens.LBRACE, KtNodeTypes.BLOCK).lineBreakInCode()
        beforeInside(KtTokens.RBRACE, KtNodeTypes.BLOCK).spacing(
          1, 0, 1,
          kotlinCommonSettings.KEEP_LINE_BREAKS,
          kotlinCommonSettings.KEEP_BLANK_LINES_BEFORE_RBRACE
        )
        between(KtTokens.LBRACE, KtNodeTypes.ENUM_ENTRY).spacing(1, 0, 0, true, kotlinCommonSettings.KEEP_BLANK_LINES_IN_CODE)
        beforeInside(KtTokens.RBRACE, KtNodeTypes.WHEN).lineBreakInCode()
        between(KtTokens.RPAR, KtNodeTypes.BODY).spaces(1)

        // if when entry has block, spacing after arrow should be set by lbrace rule
        aroundInside(KtTokens.ARROW, KtNodeTypes.WHEN_ENTRY).spaceIf(kotlinCustomSettings.SPACE_AROUND_WHEN_ARROW)

        beforeInside(KtTokens.COLON, EXTEND_COLON_ELEMENTS) { spaceIf(kotlinCustomSettings.SPACE_BEFORE_EXTEND_COLON) }

        after(KtTokens.EOL_COMMENT).lineBreakInCode()
      }
    }
}

private fun excludeLambdasAndObjects(parent: ASTBlock): List<TextRange> {
    val rangesToExclude = mutableListOf<TextRange>()
    parent.requireNode().psi.accept(object : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            super.visitLambdaExpression(lambdaExpression)
            rangesToExclude.add(lambdaExpression.textRange)
        }

        override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
            super.visitObjectLiteralExpression(expression)
            rangesToExclude.add(expression.textRange)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (function.name == null) {
                rangesToExclude.add(function.textRange)
            }
        }
    })
    return TextRangeUtil.excludeRanges(parent.textRange, rangesToExclude).toList()
}