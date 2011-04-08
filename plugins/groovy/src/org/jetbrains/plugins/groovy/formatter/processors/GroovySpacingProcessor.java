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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEditorActionUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.COMMENT_SET;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kELSE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNEW;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSWITCH;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSYNCHRONIZED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSL_COMMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.GDOC_TAG;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mCLOSABLE_BLOCK_OP;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mGDOC_ASTERISKS;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mGDOC_INLINE_TAG_END;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mGDOC_INLINE_TAG_START;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mGDOC_TAG_VALUE_RPAREN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.mRCURLY;

/**
 * @author ilyas
 */
public class GroovySpacingProcessor extends GroovyElementVisitor {
  private PsiElement myParent;
  private final CodeStyleSettings mySettings;

  private Spacing myResult;
  private ASTNode myChild1;
  private ASTNode myChild2;

  public GroovySpacingProcessor(ASTNode node, CodeStyleSettings settings) {
    mySettings = settings;

    _init(node);

    if (myChild1 == null || myChild2 == null) {
      return;
    }

    PsiElement psi1 = myChild1.getPsi();
    PsiElement psi2 = myChild2.getPsi();
    if (psi1 == null || psi2 == null) return;
    if (psi1.getLanguage() != GROOVY_LANGUAGE || psi2.getLanguage() != GROOVY_LANGUAGE) {
      return;
    }

    ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == mNLS) {
      prev = getPrevElementType(prev);
    }
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      return;
    }

    if (myChild2 != null && mySettings.KEEP_FIRST_COLUMN_COMMENT && COMMENT_SET.contains(myChild2.getElementType())) {
      if (myChild1.getElementType() != IMPORT_STATEMENT) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, 1);
      }
      return;
    }

    if (myParent instanceof GroovyPsiElement) {
      ((GroovyPsiElement) myParent).accept(this);
    }
  }

  private void _init(final ASTNode child) {
    if (child != null) {
      ASTNode treePrev = child.getTreePrev();
      while (treePrev != null && isWhiteSpace(treePrev)) {
        treePrev = treePrev.getTreePrev();
      }
      if (treePrev == null) {
        _init(child.getTreeParent());
      }
      else {
        myChild2 = child;
        myChild1 = treePrev;
        final CompositeElement parent = (CompositeElement)treePrev.getTreeParent();
        myParent = SourceTreeToPsiMap.treeElementToPsi(parent);
      }
    }
  }

  @Override
  public void visitAnnotation(GrAnnotation annotation) {
    if (myChild2.getElementType() == ANNOTATION_ARGUMENTS) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitArgumentList(GrArgumentList list) {
    if (myChild1.getElementType() == mLBRACK || myChild2.getElementType() == mRBRACK) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
    // todo add other cases
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    if (myChild2.getElementType() == ARGUMENTS) createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
  }

  public void visitClosure(GrClosableBlock closure) {
    if ((myChild1.getElementType() == mLCURLY && myChild2.getElementType() != PARAMETERS_LIST && myChild2.getElementType() != mCLOSABLE_BLOCK_OP)
        || myChild2.getElementType() == mRCURLY) {
      myResult = Spacing.createDependentLFSpacing(0, Integer.MAX_VALUE, closure.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else if (myChild1.getElementType() == mCLOSABLE_BLOCK_OP) {
      GrStatement[] statements = closure.getStatements();
      if (statements.length > 0) {
        TextRange range = new TextRange(statements[0].getTextRange().getStartOffset(), statements[statements.length - 1].getTextRange().getEndOffset());
        myResult = Spacing.createDependentLFSpacing(1, Integer.MAX_VALUE, range, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  public void visitOpenBlock(GrOpenBlock block) {
    if (myChild1.getElementType() == mLCURLY && myChild2.getElementType() == mRCURLY && block.getParent() instanceof GrBlockStatement) {
      myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else
    if (myChild1.getElementType() == mLCURLY && !GroovyEditorActionUtil.isMultilineStringElement(myChild2) ||
        myChild2.getElementType() == mRCURLY && !GroovyEditorActionUtil.isMultilineStringElement(myChild1)) {
      myResult = Spacing.createDependentLFSpacing(0, 1, block.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitNewExpression(GrNewExpression newExpression) {
    if (myChild1.getElementType() == kNEW) {
      createSpaceInCode(true);
    } else if (myChild2.getElementType() == ARGUMENTS) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (myChild2.getElementType() == CLASS_BODY) {
      PsiIdentifier nameIdentifier = typeDefinition.getNameIdentifier();
      int dependanceStart = nameIdentifier == null ? myParent.getTextRange().getStartOffset() : nameIdentifier.getTextRange().getStartOffset();
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CLASS_LBRACE, mySettings.CLASS_BRACE_STYLE,
                                      new TextRange(dependanceStart, myChild1.getTextRange().getEndOffset()), false);
    }
  }

  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    if (myChild1.getElementType() == mLCURLY && myChild2.getElementType() == mRCURLY) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    } else if (myChild1.getElementType() == mLCURLY) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_CLASS_HEADER + 1,
                                       mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    } else if (myChild2.getElementType() == mRCURLY) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }
  }

  public void visitMethod(GrMethod method) {
    if (myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    } else if (myChild2.getElementType() == mRPAREN && myChild2.getElementType() == THROW_CLAUSE) {
      createSpaceInCode(true);
    } else if (isOpenBlock(myChild2)) {
      PsiElement methodName = method.getNameIdentifier();
      int dependancyStart = methodName == null ? myParent.getTextRange().getStartOffset() : methodName.getTextRange().getStartOffset();
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_METHOD_LBRACE, mySettings.METHOD_BRACE_STYLE,
                                      new TextRange(dependancyStart, myChild1.getTextRange().getEndOffset()), mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);
    } else if (myChild1.getElementType() == MODIFIERS) {
      processModifierList(myChild1);
    } else if (COMMENT_SET.contains(myChild1.getElementType())
               && (myChild2.getElementType() == MODIFIERS || myChild2.getElementType() == REFERENCE_ELEMENT)) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
    }

  }

  private static boolean isOpenBlock(final ASTNode node) {
    return node.getElementType() == OPEN_BLOCK || node.getElementType() == CONSTRUCTOR_BODY;
  }

  public void visitDocMethodReference(GrDocMethodReference reference) {
    visitDocMember();
  }


  public void visitDocFieldReference(GrDocFieldReference reference) {
    visitDocMember();
  }

  private void visitDocMember() {
    myResult = Spacing.createSpacing(0, 0, 0, false, 0);
  }

  public void visitDocMethodParameterList(GrDocMethodParams params) {
    if (myChild1.getElementType() == mGDOC_TAG_VALUE_LPAREN || myChild2.getElementType() == mGDOC_TAG_VALUE_RPAREN) {
      myResult = Spacing.createSpacing(0, 0, 0, false, 0);
      return;
    }
    if (myChild2.getElementType() == mGDOC_TAG_VALUE_COMMA) {
      myResult = Spacing.createSpacing(0, 0, 0, false, 0);
      return;
    }
    createSpaceInCode(true);
  }

  public void visitDocMethodParameter(GrDocMethodParameter parameter) {
    if (myChild1.getTreePrev() == null) {
      createSpaceInCode(true);
    }
  }

  public void visitWhileStatement(GrWhileStatement statement) {
    if (myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    } else if (myChild1.getElementType() == mLPAREN || myChild2.getElementType() == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    } else if (myChild2.getPsi() instanceof GrBlockStatement) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_WHILE_LBRACE, mySettings.BRACE_STYLE,
                                      new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()), mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    } else {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  public void visitCatchClause(GrCatchClause catchClause) {
    if (isOpenBlock(myChild2)) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null,
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  public void visitFinallyClause(GrFinallyClause catchClause) {
    if (isOpenBlock(myChild2)) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null,
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    if (myChild2.getElementType() == FINALLY_CLAUSE) {
      processOnNewLineCondition(mySettings.FINALLY_ON_NEW_LINE);
    } else if (isOpenBlock(myChild2)) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null,
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    } else if (myChild2.getElementType() == CATCH_CLAUSE) {
      processOnNewLineCondition(mySettings.CATCH_ON_NEW_LINE);
    }
  }

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    if (myChild1.getElementType() == kSWITCH && myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
    } else if (myChild1.getElementType() == mLPAREN || myChild2.getElementType() == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    } else if (myChild2.getElementType() == mLCURLY) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE, null,
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    if (myChild1.getElementType() == kSYNCHRONIZED || myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
    } else if (myChild1.getElementType() == mLPAREN || myChild2.getElementType() == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
    } else if (isOpenBlock(myChild2)) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE,
                                      mySettings.BRACE_STYLE, null,
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }

  }

  public void visitDocComment(GrDocComment comment) {
    if (myChild1.getElementType() == GDOC_TAG &&
        myChild2.getElementType() == GDOC_TAG &&
        mySettings.JD_LEADING_ASTERISKS_ARE_ENABLED) {
      IElementType type = myChild1.getLastChildNode().getElementType();
      if (type == mGDOC_ASTERISKS) {
        myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  public void visitDocTag(GrDocTag docTag) {
    if (myChild1.getElementType() == mGDOC_INLINE_TAG_START ||
        myChild2.getElementType() == mGDOC_INLINE_TAG_END) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    if (myChild2.getElementType() == kELSE) {
      if (!isOpenBlock(myChild1) && myChild1.getElementType() != BLOCK_STATEMENT) {
        myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        if (mySettings.ELSE_ON_NEW_LINE) {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        } else {
          createSpaceProperty(true, false, 0);
        }
      }
    } else if (myChild1.getElementType() == kELSE) {
      if (myChild2.getElementType() == IF_STATEMENT) {
        if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
          createSpaceProperty(true, false, 0);
        } else {
          myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
      } else {
        if (myChild2.getElementType() == BLOCK_STATEMENT || isOpenBlock(myChild2)) {
          myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_ELSE_LBRACE, mySettings.BRACE_STYLE,
                                          null,
                                          mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
        } else {
          createSpacingBeforeElementInsideControlStatement();
        }
      }
    } else if (myChild2.getElementType() == BLOCK_STATEMENT || isOpenBlock(myChild2)) {
      boolean space = myChild2.getPsi() == ((GrIfStatement) myParent).getElseBranch() ? mySettings.SPACE_BEFORE_ELSE_LBRACE : mySettings.SPACE_BEFORE_IF_LBRACE;
      myResult = getSpaceBeforeLBrace(space, mySettings.BRACE_STYLE, new TextRange(myParent.getTextRange().getStartOffset(),
                                                                                   myChild1.getTextRange().getEndOffset()),
                                      mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    } else if (myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_IF_PARENTHESES);
    } else if (myChild1.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    } else if (myChild2.getElementType() == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    } else if (((GrIfStatement) myParent).getThenBranch() == myChild2.getPsi()) {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  public void visitForStatement(GrForStatement forStatement) {
    if (myChild2.getElementType() == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    } else if (myChild1.getElementType() == mLPAREN) {
      ASTNode rparenth = findFrom(myChild2, mRPAREN, true);
      if (rparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenSpace(mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                         new TextRange(myChild1.getTextRange().getStartOffset(), rparenth.getTextRange().getEndOffset()));
      }
    } else if (myChild2.getElementType() == mRPAREN) {
      ASTNode lparenth = findFrom(myChild2, mLPAREN, false);
      if (lparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenSpace(mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                         new TextRange(lparenth.getTextRange().getStartOffset(), myChild2.getTextRange().getEndOffset()));
      }

    } else if (myChild2.getElementType() == BLOCK_STATEMENT || isOpenBlock(myChild2)) {
      if (myChild2.getElementType() == BLOCK_STATEMENT) {
        myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FOR_LBRACE, mySettings.BRACE_STYLE,
                                        new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()), mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
      } else if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE) {
        myResult = Spacing.createDependentLFSpacing(1, 1, myParent.getTextRange(), false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  private void createParenSpace(final boolean onNewLine, final boolean space) {
    createParenSpace(onNewLine, space, myParent.getTextRange());
  }

  private void createParenSpace(final boolean onNewLine, final boolean space, final TextRange dependance) {
    if (onNewLine) {
      final int spaces = space ? 1 : 0;
      myResult = Spacing
        .createDependentLFSpacing(spaces, spaces, dependance, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      createSpaceInCode(space);
    }
  }


  private static ASTNode findFrom(ASTNode current, final IElementType expected, boolean forward) {
    while (current != null) {
      if (current.getElementType() == expected) return current;
      current = forward ? current.getTreeNext() : current.getTreePrev();
    }
    return null;
  }


  private void processOnNewLineCondition(final boolean onNewLine) {
    if (onNewLine) {
      if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Spacing.createDependentLFSpacing(0, 1, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    } else {
      createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }


  private void createSpacingBeforeElementInsideControlStatement() {
    if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE && myChild1.getElementType() != mSL_COMMENT) {
      createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }


  private void processModifierList(ASTNode modifierList) {
    if (modifierList.getLastChildNode().getElementType() == ANNOTATION && mySettings.METHOD_ANNOTATION_WRAP == CodeStyleSettings.WRAP_ALWAYS) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (mySettings.MODIFIER_LIST_WRAP) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      createSpaceProperty(true, false, 0);
    }
  }

  public Spacing getSpacing() {
    return myResult;
  }

  private void createSpaceInCode(final boolean space) {
    createSpaceProperty(space, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private void createSpaceProperty(boolean space, int keepBlankLines) {
    createSpaceProperty(space, mySettings.KEEP_LINE_BREAKS, keepBlankLines);
  }

  private void createSpaceProperty(boolean space, boolean keepLineBreaks, final int keepBlankLines) {
    myResult = Spacing.createSpacing(space ? 1 : 0, space ? 1 : 0, 0, keepLineBreaks, keepBlankLines);
  }

  private Spacing getSpaceBeforeLBrace(final boolean spaceBeforeLbrace, int braceStyle, TextRange dependantRange, boolean keepOneLine) {
    if (dependantRange != null && braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      int space = spaceBeforeLbrace ? 1 : 0;
      return createNonLFSpace(space, dependantRange, false);
    } else if (braceStyle == CodeStyleSettings.END_OF_LINE || braceStyle == CodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      int space = spaceBeforeLbrace ? 1 : 0;
      return createNonLFSpace(space, null, false);
    } else if (keepOneLine) {
      int space = spaceBeforeLbrace ? 1 : 0;
      return Spacing.createDependentLFSpacing(space, space, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      return Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private Spacing createNonLFSpace(int spaces, final TextRange dependantRange, final boolean keepLineBreaks) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else if (dependantRange != null) {
      return Spacing.createDependentLFSpacing(spaces, spaces, dependantRange, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      return Spacing.createSpacing(spaces, spaces, 0, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  static boolean isWhiteSpace(final ASTNode treePrev) {
    return treePrev != null && (treePrev.getPsi() instanceof PsiWhiteSpace || treePrev.getTextLength() == 0);
  }

  static ASTNode getPrevElementType(final ASTNode child) {
    return FormatterUtil.getLeafNonSpaceBefore(child);
  }
}

