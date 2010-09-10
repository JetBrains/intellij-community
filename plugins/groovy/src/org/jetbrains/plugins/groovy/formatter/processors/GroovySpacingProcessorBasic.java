/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

/**
 * @author ilyas
 */
public abstract class GroovySpacingProcessorBasic extends SpacingTokens implements GroovyElementTypes {

  private static final Spacing NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  private static final Spacing NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  private static final Spacing COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  private static final Spacing COMMON_SPACING_WITH_NL = Spacing.createSpacing(1, 1, 1, true, 100);
  private static final Spacing IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  private static final Spacing IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);
  private static final Spacing LAZY_SPACING = Spacing.createSpacing(0, 239, 0, true, 100);

  public static Spacing getSpacing(GroovyBlock child1, GroovyBlock child2, CodeStyleSettings settings) {

    ASTNode leftNode = child1.getNode();
    ASTNode rightNode = child2.getNode();

    //Braces Placement
    // For multi-line strings
    if (!child1.getNode().getTextRange().equals(child1.getTextRange()) || !child2.getNode().getTextRange().equals(child2.getTextRange())) {
      return NO_SPACING;
    }

    //For type parameters
    IElementType leftType = leftNode.getElementType();
    if (mLT == leftType && rightNode.getPsi() instanceof GrTypeParameter ||
        mGT == rightNode.getElementType() && leftNode.getPsi() instanceof GrTypeParameter ||
        mIDENT == leftType && rightNode.getPsi() instanceof GrTypeParameterList) {
      return NO_SPACING;
    }

    // For left parentheses in method declarations or calls
    if (mLPAREN.equals(rightNode.getElementType()) &&
        rightNode.getPsi().getParent().getNode() != null &&
        METHOD_DEFS.contains(rightNode.getPsi().getParent().getNode().getElementType())) {
      return NO_SPACING;
    }

    if (ARGUMENTS.equals(rightNode.getElementType())) {
      return NO_SPACING;
    }
    // For left square bracket in array declarations and selections by index
    if ((mLBRACK.equals(rightNode.getElementType()) &&
         rightNode.getPsi().getParent().getNode() != null &&
         INDEX_OR_ARRAY.contains(rightNode.getPsi().getParent().getNode().getElementType())) ||
        ARRAY_DECLARATOR.equals(rightNode.getElementType())) {
      return NO_SPACING;
    }

    if (METHOD_DEFS.contains(leftType)) {
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD + 1, settings.KEEP_LINE_BREAKS, 100);
    }

    if (METHOD_DEFS.contains(rightNode.getElementType())) {
      if (leftNode.getElementType() == GROOVY_DOC_COMMENT) {
        return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD, settings.KEEP_LINE_BREAKS, 0);
      }
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD + 1, settings.KEEP_LINE_BREAKS, 100);
    }

    IElementType rightType = rightNode.getElementType();
    
    if (leftType == mLCURLY && rightType == PARAMETERS_LIST) { //closure
      return LAZY_SPACING;
    }

    // For parentheses in arguments and typecasts
    if (LEFT_BRACES.contains(leftType) || RIGHT_BRACES.contains(rightType)) {
      return NO_SPACING_WITH_NEWLINE;
    }
    // For type parameters
    if ((mLT.equals(leftType) || mGT.equals(rightNode.getElementType())) &&
        leftNode.getPsi().getParent() != null &&
        leftNode.getPsi().getParent() instanceof GrTypeArgumentList) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (rightNode.getPsi() != null && rightNode.getPsi() instanceof GrTypeArgumentList) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** punctuation marks ************/
    // For dots, commas etc.
    if ((PUNCTUATION_SIGNS.contains(rightNode.getElementType())) ||
        (mCOLON.equals(rightNode.getElementType()) && !(rightNode.getPsi().getParent() instanceof GrConditionalExpression))) {
      return NO_SPACING;
    }

    if (GroovyTokenTypes.DOTS.contains(leftType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** imports ************/
    if (IMPORT_STATEMENT.equals(leftType) && IMPORT_STATEMENT.equals(rightNode.getElementType())) {
      return IMPORT_BETWEEN_SPACING;
    }
    if ((IMPORT_STATEMENT.equals(leftType) &&
         (!IMPORT_STATEMENT.equals(rightNode.getElementType()) && !mSEMI.equals(rightNode.getElementType()))) ||
        ((!IMPORT_STATEMENT.equals(leftType) && !mSEMI.equals(leftType)) && IMPORT_STATEMENT.equals(rightNode.getElementType()))) {
      return IMPORT_OTHER_SPACING;
    }

    //todo:check it for multiple assignments
    if ((VARIABLE_DEFINITION.equals(leftType) || VARIABLE_DEFINITION.equals(rightNode.getElementType())) &&
        !(leftNode.getTreeNext() instanceof PsiErrorElement)) {
      return Spacing.createSpacing(0, 0, 1, false, 100);
    }

/********** exclusions ************/
    // For << and >> ...
    if ((mLT.equals(leftType) && mLT.equals(rightNode.getElementType())) ||
        (mGT.equals(leftType) && mGT.equals(rightNode.getElementType()))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // Unary and postfix expressions
    if (PREFIXES.contains(leftType) ||
        POSTFIXES.contains(rightNode.getElementType()) ||
        (PREFIXES_OPTIONAL.contains(leftType) && leftNode.getPsi().getParent() instanceof GrUnaryExpression)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (RANGES.contains(leftType) || RANGES.contains(rightNode.getElementType())) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // For Gstrings and regexes
    if (leftNode.getPsi().getParent() != null &&
        leftNode.getPsi().getParent().equals(rightNode.getPsi().getParent()) &&
        leftNode.getPsi().getParent() instanceof GrString) {
      return NO_SPACING;
    }
    if (isDollarInGStringInjection(leftNode) || isDollarInGStringInjection(rightNode)) {
      return NO_SPACING;
    }
    if (leftNode.getPsi().getParent() instanceof GrStringInjection &&
        rightNode.getPsi().getParent() instanceof GrString &&
        rightNode.getPsi().getParent().equals(leftNode.getPsi().getParent().getParent())) {
      return NO_SPACING;
    }

    if (mGDOC_ASTERISKS == leftType && mGDOC_COMMENT_DATA == rightNode.getElementType()) {
      String text = rightNode.getText();
      if (text.length() > 0 && !text.startsWith(" ")) {
        return COMMON_SPACING;
      }
      return NO_SPACING;
    }

    if (leftType == mGDOC_TAG_VALUE_TOKEN && rightType == mGDOC_COMMENT_DATA) {
      return LAZY_SPACING;
    }

    final PsiElement left = leftNode.getPsi();
    final PsiElement right = rightNode.getPsi();
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

    return COMMON_SPACING;
  }

  private static boolean isDollarInGStringInjection(ASTNode node) {
    return node.getElementType() == mDOLLAR && node.getTreeParent().getElementType() == GSTRING_INJECTION;
  }
}
