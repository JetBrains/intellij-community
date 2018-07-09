// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.blocks.ClosureBodyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GrLabelBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.MethodCallWithoutQualifierBlock;
import org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.RANGES;

/**
 * @author ilyas
 */
public abstract class GroovySpacingProcessorBasic {

  private static final Spacing NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  private static final Spacing NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  private static final Spacing COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  private static final Spacing COMMON_SPACING_WITH_NL = Spacing.createSpacing(1, 1, 1, true, 100);
  private static final Spacing LAZY_SPACING = Spacing.createSpacing(0, 239, 0, true, 100);

  public static Spacing getSpacing(GroovyBlock child1,
                                   GroovyBlock child2,
                                   FormattingContext context) {

    ASTNode leftNode = child1.getNode();
    ASTNode rightNode = child2.getNode();
    final PsiElement left = leftNode.getPsi();
    final PsiElement right = rightNode.getPsi();

    IElementType leftType = leftNode.getElementType();
    IElementType rightType = rightNode.getElementType();

    final CommonCodeStyleSettings settings = context.getSettings();
    final GroovyCodeStyleSettings groovySettings = context.getGroovySettings();

    if (!(mirrorsAst(child1) && mirrorsAst(child2))) {
      return NO_SPACING;
    }

    if (child2 instanceof ClosureBodyBlock) {
      return settings.SPACE_WITHIN_BRACES ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }

    if (child1 instanceof ClosureBodyBlock) {
      return createDependentSpacingForClosure(settings, groovySettings, (GrClosableBlock)left.getParent(), false);
    }

    if (leftType == GroovyDocElementTypes.GROOVY_DOC_COMMENT) {
      return COMMON_SPACING_WITH_NL;
    }

    if (right instanceof GrTypeArgumentList) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** punctuation marks ************/
    if (GroovyTokenTypes.mCOMMA == leftType) {
      return settings.SPACE_AFTER_COMMA ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (GroovyTokenTypes.mCOMMA == rightType) {
      return settings.SPACE_BEFORE_COMMA ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (GroovyTokenTypes.mSEMI == leftType) {
      return settings.SPACE_AFTER_SEMICOLON ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (GroovyTokenTypes.mSEMI == rightType) {
      return settings.SPACE_BEFORE_SEMICOLON ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    // For dots, commas etc.
    if ((TokenSets.DOTS.contains(rightType)) ||
        (GroovyTokenTypes.mCOLON.equals(rightType) && !(right.getParent() instanceof GrConditionalExpression))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (TokenSets.DOTS.contains(leftType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    //todo:check it for multiple assignments
    if ((GroovyElementTypes.VARIABLE_DEFINITION.equals(leftType) || GroovyElementTypes.VARIABLE_DEFINITION.equals(rightType)) &&
        !(leftNode.getTreeNext() instanceof PsiErrorElement)) {
      return Spacing.createSpacing(0, 0, 1, false, 100);
    }

    // For regexes
    if (leftNode.getTreeParent().getElementType() == GroovyTokenTypes.mREGEX_LITERAL ||
        leftNode.getTreeParent().getElementType() == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      return NO_SPACING;
    }

/********** exclusions ************/
    // For << and >> ...
    if ((GroovyTokenTypes.mLT.equals(leftType) && GroovyTokenTypes.mLT.equals(rightType)) ||
        (GroovyTokenTypes.mGT.equals(leftType) && GroovyTokenTypes.mGT.equals(rightType))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // Unary and postfix expressions
    if (SpacingTokens.PREFIXES.contains(leftType) ||
        SpacingTokens.POSTFIXES.contains(rightType) ||
        (SpacingTokens.PREFIXES_OPTIONAL.contains(leftType) && left.getParent() instanceof GrUnaryExpression)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (RANGES.contains(leftType) || RANGES.contains(rightType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (GroovyDocTokenTypes.mGDOC_ASTERISKS == leftType && GroovyDocTokenTypes.mGDOC_COMMENT_DATA == rightType) {
      String text = rightNode.getText();
      if (!text.isEmpty() && !StringUtil.startsWithChar(text, ' ')) {
        return COMMON_SPACING;
      }
      return NO_SPACING;
    }

    if (leftType == GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN && rightType == GroovyDocTokenTypes.mGDOC_COMMENT_DATA) {
      return LAZY_SPACING;
    }

    if (left instanceof GrStatement &&
        right instanceof GrStatement &&
        left.getParent() instanceof GrStatementOwner &&
        right.getParent() instanceof GrStatementOwner) {
      return COMMON_SPACING_WITH_NL;
    }

    if (rightType == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END ||
        leftType == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START ||
        rightType == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START ||
        leftType == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END) {
      return NO_SPACING;
    }

    if ((leftType == GroovyDocElementTypes.GDOC_INLINED_TAG && rightType == GroovyDocTokenTypes.mGDOC_COMMENT_DATA)
      || (leftType == GroovyDocTokenTypes.mGDOC_COMMENT_DATA && rightType == GroovyDocElementTypes.GDOC_INLINED_TAG))
    {
      // Keep formatting between groovy doc text and groovy doc reference tag as is.
      return NO_SPACING;
    }

    if (leftType == GroovyElementTypes.CLASS_TYPE_ELEMENT && rightType == GroovyTokenTypes.mTRIPLE_DOT) {
      return NO_SPACING;
    }

    // diamonds
    if (rightType == GroovyTokenTypes.mLT || rightType == GroovyTokenTypes.mGT) {
      if (right.getParent() instanceof GrCodeReferenceElement) {
        PsiElement p = right.getParent().getParent();
        if (p instanceof GrNewExpression || p instanceof GrAnonymousClassDefinition) {
          return NO_SPACING;
        }
      }
    }

    return COMMON_SPACING;
  }

  @NotNull
  static Spacing createDependentSpacingForClosure(@NotNull CommonCodeStyleSettings settings,
                                                  @NotNull GroovyCodeStyleSettings groovySettings,
                                                  @NotNull GrClosableBlock closure,
                                                  final boolean forArrow) {
    boolean spaceWithinBraces = closure.getParent() instanceof GrStringInjection
                                ? groovySettings.SPACE_WITHIN_GSTRING_INJECTION_BRACES
                                : settings.SPACE_WITHIN_BRACES;
    GrStatement[] statements = closure.getStatements();
    if (statements.length > 0) {
      final PsiElement startElem = forArrow ? statements[0] : closure;
      int start = startElem.getTextRange().getStartOffset();
      int end = statements[statements.length - 1].getTextRange().getEndOffset();
      TextRange range = new TextRange(start, end);

      int minSpaces = spaceWithinBraces || forArrow ? 1 : 0;
      int maxSpaces = spaceWithinBraces || forArrow ? 1 : 0;
      return Spacing.createDependentLFSpacing(minSpaces, maxSpaces, range, settings.KEEP_LINE_BREAKS, settings.KEEP_BLANK_LINES_IN_CODE);
    }
    return spaceWithinBraces || forArrow ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
  }

  private static boolean mirrorsAst(GroovyBlock block) {
    return block.getNode().getTextRange().equals(block.getTextRange()) ||
           block instanceof MethodCallWithoutQualifierBlock ||
           block instanceof ClosureBodyBlock ||
           block instanceof GrLabelBlock;
  }
}
