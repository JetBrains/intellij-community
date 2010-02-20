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

package org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author ilyas
 */
public class StatementMover extends LineMover {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown.StatementMover");

  public StatementMover(boolean down) {
    super(down);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    final boolean available = super.checkAvailable(editor, file);
    if (!available) return false;
    LineRange range = toMove;

    if (editor == null) return false;
    final Document document = editor.getDocument();

    range = expandLineRangeToCoverPsiElements(range, editor, file);
    if (range == null) return false;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0));
    final PsiElement[] statements = GroovyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset, false);
    if (statements.length == 0) return false;
    for (final PsiElement statement : statements) {
      if (statement instanceof GrMembersDeclaration) {
        final GrMembersDeclaration declaration = (GrMembersDeclaration)statement;
        if (declaration.getMembers().length > 0) {
          return false;
        }
      }
    }


    range = toMove = new LineRange(statements[0], statements[statements.length - 1], document);

    updateComplementaryRange();

    final PsiElement commonParent = PsiTreeUtil.findCommonParent(range.firstElement, range.lastElement);

    // cannot move in/outside method/class/comment
    Class<? extends PsiElement>[] classes = new Class[]{GrMethod.class, GrTypeDefinition.class, PsiComment.class, GroovyFile.class};
    PsiElement guard = PsiTreeUtil.getParentOfType(commonParent, classes);
    if (!calcInsertOffset(file, editor, range)) return false;
    int insertOffset = isDown ? getLineStartSafeOffset(document, toMove2.endLine) - 1 : document.getLineStartOffset(toMove2.startLine);

    PsiElement newGuard = file.getViewProvider().findElementAt(insertOffset);
    newGuard = PsiTreeUtil.getParentOfType(newGuard, classes);
    if (newGuard != null && newGuard != guard && isInside(insertOffset, newGuard) != PsiTreeUtil.isAncestor(newGuard, range.lastElement, false)) {
      if (!PsiTreeUtil.isAncestor(guard, newGuard, false)) {
        return false;
      }
      while (true) {
        PsiElement candidate = PsiTreeUtil.getParentOfType(newGuard, classes);
        if (candidate == null || candidate == guard || !PsiTreeUtil.isAncestor(guard, candidate, false)) {
          break;
        }
        newGuard = candidate;
      }
      toMove2 = new LineRange(newGuard, newGuard, document);
    }

    return true;
  }

  @Nullable
  private static LineRange expandLineRangeToCoverPsiElements(final LineRange range, Editor editor, final PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    if (parent instanceof LeafPsiElement && parent.getParent() instanceof GrLiteralImpl) {
      return null; //multiline GString
    }

    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    if (elementRange == null) return null;
    int endOffset = elementRange.getSecond().getTextRange().getEndOffset();
    Document document = editor.getDocument();
    if (endOffset > document.getTextLength()) {
      LOG.assertTrue(!PsiDocumentManager.getInstance(file.getProject()).isUncommited(document));
      LOG.assertTrue(PsiDocumentManagerImpl.checkConsistency(file, document));
    }
    int endLine;
    if (endOffset == document.getTextLength()) {
      endLine = document.getLineCount();
    } else {
      endLine = editor.offsetToLogicalPosition(endOffset).line + 1;
      endLine = Math.min(endLine, document.getLineCount());
    }
    int startLine = Math.min(range.startLine, editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line);
    endLine = Math.max(endLine, range.endLine);
    return new LineRange(startLine, endLine);
  }

  private static boolean isInside(final int offset, final PsiElement guard) {
    if (guard == null) return false;

    TextRange inside = null;
    if (guard instanceof GrMethod) {
      GrOpenBlock block = ((GrMethod) guard).getBlock();
      if (block != null) inside = block.getTextRange();
    } else if (guard instanceof GrClosableBlock) {
      inside = guard.getTextRange();
    } else if (guard instanceof GrTypeDefinition) {
      GrTypeDefinitionBody body = ((GrTypeDefinition) guard).getBody();
      if (body != null) {
        final PsiElement lBrace = body.getLBrace();
        if (lBrace != null) {
          inside = new TextRange(lBrace.getTextOffset(), body.getTextRange().getEndOffset());
        }
      }

    }
    return inside != null && inside.contains(offset);
  }

  private boolean calcInsertOffset(PsiFile file, final Editor editor, LineRange range) {
    int line = isDown ? range.endLine + 1 : range.startLine - 1;
    int startLine = isDown ? range.endLine : range.startLine - 1;
    if (line < 0 || startLine < 0) return false;

    while (true) {
      final int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      if (offset == file.getTextLength()) {
        return true;
      }

      PsiElement element = firstNonWhiteElement(offset, file, true, true);

      while (element != null && !(element instanceof PsiFile)) {
        if (!element.getTextRange().grown(-1).shiftRight(1).contains(offset)) {
          boolean found = false;
          if ((element instanceof GrTopStatement || element instanceof PsiComment)
              && statementCanBePlacedAlong(element)) {
            found = true;
          } else if (element.getNode() != null &&
              element.getNode().getElementType() == GroovyTokenTypes.mRCURLY) {
            // before code block closing brace
            found = true;
          }
          if (found) {
            toMove = range;
            int endLine = line;
            if (startLine > endLine) {
              int tmp = endLine;
              endLine = startLine;
              startLine = tmp;
            }
//            startLine--;
//            endLine--;
            toMove2 = isDown ? new LineRange(startLine, endLine) : new LineRange(startLine, endLine + 1);
            return true;
          }
        }
        element = element.getParent();
      }
      line += isDown ? 1 : -1;
      if (line == 0 || line >= editor.getDocument().getLineCount()) {
        return false;
      }
    }
  }

  private static boolean statementCanBePlacedAlong(final PsiElement element) {
    if (element instanceof GrBlockStatement) return false;
    final PsiElement parent = element.getParent();
    if (parent instanceof GrCodeBlock) return true;
    if (parent instanceof GroovyFile) return true;
    if (parent instanceof GrIfStatement &&
        (element == ((GrIfStatement) parent).getThenBranch() || element == ((GrIfStatement) parent).getElseBranch())) {
      return true;
    }
    if (parent instanceof GrWhileStatement && element == ((GrWhileStatement) parent).getBody()) {
      return true;
    }
    if (parent instanceof GrForStatement && element == ((GrForStatement) parent).getBody()) {
      return true;
    }
    // know nothing about that
    return false;
  }

}
