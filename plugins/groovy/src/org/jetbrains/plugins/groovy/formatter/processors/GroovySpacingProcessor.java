/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;

/**
 * @author ilyas
 */
public abstract class GroovySpacingProcessor extends SpacingTokens implements GroovyElementTypes {

  private static final Spacing NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  private static final Spacing NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  private static final Spacing COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  private static final Spacing IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  private static final Spacing IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);

  public static Spacing getSpacing(GroovyBlock child1, GroovyBlock child2) {

    ASTNode leftNode = child1.getNode();
    ASTNode rightNode = child2.getNode();

/********** Braces ************/
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
    // For parentheses in arguments and typecasts
    if (LEFT_BRACES.contains(leftNode.getElementType()) ||
            RIGHT_BRACES.contains(rightNode.getElementType())) {
      return NO_SPACING_WITH_NEWLINE;
    }
    // For type parameters
    if ((mLT.equals(leftNode.getElementType()) || mGT.equals(rightNode.getElementType())) &&
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
            (mCOLON.equals(rightNode.getElementType()) &&
                    !(rightNode.getPsi().getParent() instanceof GrConditionalExpression))) {
      return NO_SPACING;
    }

    if (GroovyTokenTypes.DOTS.contains(leftNode.getElementType())) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** imports ************/
    if (IMPORT_STATEMENT.equals(leftNode.getElementType()) &&
            IMPORT_STATEMENT.equals(rightNode.getElementType())) {
      return IMPORT_BETWEEN_SPACING;
    }
    if ((IMPORT_STATEMENT.equals(leftNode.getElementType()) &&
            (!IMPORT_STATEMENT.equals(rightNode.getElementType()) &&
                    !mSEMI.equals(rightNode.getElementType()))) ||
            ((!IMPORT_STATEMENT.equals(leftNode.getElementType()) &&
                    !mSEMI.equals(leftNode.getElementType())) &&
                    IMPORT_STATEMENT.equals(rightNode.getElementType()))) {
      return IMPORT_OTHER_SPACING;
    }

/********** exclusions ************/
    // For << and >> ...
    if ((mLT.equals(leftNode.getElementType()) && mLT.equals(rightNode.getElementType())) ||
            (mGT.equals(leftNode.getElementType()) && mGT.equals(rightNode.getElementType()))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // Unary and postfix expressions
    if (PREFIXES.contains(leftNode.getElementType()) ||
            POSTFIXES.contains(rightNode.getElementType()) ||
            (PREFIXES_OPTIONAL.contains(leftNode.getElementType()) &&
                    leftNode.getPsi().getParent() instanceof GrUnaryExpression)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (RANGES.contains(leftNode.getElementType()) ||
            RANGES.contains(rightNode.getElementType())) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // For Gstrings and regexes
    if (leftNode.getPsi().getParent() != null &&
            leftNode.getPsi().getParent().equals(rightNode.getPsi().getParent()) &&
            leftNode.getPsi().getParent() instanceof GrString
            ) {
      return null;
    }

    return COMMON_SPACING;
  }

}
