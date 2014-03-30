/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.*;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_ASTERISKS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GDOC_INLINED_TAG;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mTRIPLE_DOT;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.DOTS;

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

    if (leftType == GROOVY_DOC_COMMENT) {
      return COMMON_SPACING_WITH_NL;
    }

    if (right instanceof GrTypeArgumentList) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** punctuation marks ************/
    if (mCOMMA == leftType) {
      return settings.SPACE_AFTER_COMMA ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (mCOMMA == rightType) {
      return settings.SPACE_BEFORE_COMMA ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (mSEMI == leftType) {
      return settings.SPACE_AFTER_SEMICOLON ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    if (mSEMI == rightType) {
      return settings.SPACE_BEFORE_SEMICOLON ? COMMON_SPACING : NO_SPACING_WITH_NEWLINE;
    }
    // For dots, commas etc.
    if ((DOTS.contains(rightType)) ||
        (mCOLON.equals(rightType) && !(right.getParent() instanceof GrConditionalExpression))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (DOTS.contains(leftType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    //todo:check it for multiple assignments
    if ((VARIABLE_DEFINITION.equals(leftType) || VARIABLE_DEFINITION.equals(rightType)) &&
        !(leftNode.getTreeNext() instanceof PsiErrorElement)) {
      return Spacing.createSpacing(0, 0, 1, false, 100);
    }

    // For regexes
    if (leftNode.getTreeParent().getElementType() == mREGEX_LITERAL ||
        leftNode.getTreeParent().getElementType() == mDOLLAR_SLASH_REGEX_LITERAL) {
      return NO_SPACING;
    }

/********** exclusions ************/
    // For << and >> ...
    if ((mLT.equals(leftType) && mLT.equals(rightType)) ||
        (mGT.equals(leftType) && mGT.equals(rightType))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // Unary and postfix expressions
    if (PREFIXES.contains(leftType) ||
        POSTFIXES.contains(rightType) ||
        (PREFIXES_OPTIONAL.contains(leftType) && left.getParent() instanceof GrUnaryExpression)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (RANGES.contains(leftType) || RANGES.contains(rightType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (mGDOC_ASTERISKS == leftType && mGDOC_COMMENT_DATA == rightType) {
      String text = rightNode.getText();
      if (text.length() > 0 && !StringUtil.startsWithChar(text, ' ')) {
        return COMMON_SPACING;
      }
      return NO_SPACING;
    }

    if (leftType == mGDOC_TAG_VALUE_TOKEN && rightType == mGDOC_COMMENT_DATA) {
      return LAZY_SPACING;
    }

    if (left instanceof GrStatement &&
        right instanceof GrStatement &&
        left.getParent() instanceof GrStatementOwner &&
        right.getParent() instanceof GrStatementOwner) {
      return COMMON_SPACING_WITH_NL;
    }

    if (rightType == mGDOC_INLINE_TAG_END ||
        leftType == mGDOC_INLINE_TAG_START ||
        rightType == mGDOC_INLINE_TAG_START ||
        leftType == mGDOC_INLINE_TAG_END) {
      return NO_SPACING;
    }

    if ((leftType == GDOC_INLINED_TAG && rightType == mGDOC_COMMENT_DATA)
      || (leftType == mGDOC_COMMENT_DATA && rightType == GDOC_INLINED_TAG))
    {
      // Keep formatting between groovy doc text and groovy doc reference tag as is.
      return NO_SPACING;
    }

    if (leftType == CLASS_TYPE_ELEMENT && rightType == mTRIPLE_DOT) {
      return NO_SPACING;
    }

    // diamonds
    if (rightType == mLT || rightType == mGT) {
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
