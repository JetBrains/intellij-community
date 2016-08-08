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

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GroovyStatementMover extends StatementUpDownMover {

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    final Project project = file.getProject();
    if (!HandlerUtils.canBeInvoked(editor, project) || !(file instanceof GroovyFileBase)) return false;

    LineRange range = getLineRangeFromSelection(editor);

    final Document document = editor.getDocument();
    final int offset = document.getLineStartOffset(range.startLine);
    final GrLiteral literal = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrLiteral.class, false);
    if (literal != null && literal.textContains('\n')) return false; //multiline string

    final GroovyPsiElement pivot = getElementToMove((GroovyFileBase)file, offset);
    if (pivot == null) return false;

    final LineRange pivotRange = getLineRange(pivot);
    range = new LineRange(Math.min(range.startLine, pivotRange.startLine), Math.max(range.endLine, pivotRange.endLine));

    final GroovyPsiElement scope = PsiTreeUtil.getParentOfType(pivot, GrMethod.class, GrTypeDefinitionBody.class, GroovyFileBase.class);

    final boolean stmtLevel = isStatement(pivot);
    boolean topLevel = pivot instanceof GrTypeDefinition && pivot.getParent() instanceof GroovyFileBase;
    final List<LineRange> allRanges = allRanges(scope, stmtLevel, topLevel);
    LineRange prev = null;
    LineRange next = null;

    for (LineRange each : allRanges) {
      if (each.endLine <= range.startLine) {
        prev = each;
      }
      if (each.containsLine(range.startLine)) {
        range = new LineRange(each.startLine, range.endLine);
      }
      if (each.startLine < range.endLine && each.endLine > range.endLine) {
        range = new LineRange(range.startLine, each.endLine);
      }
      if (each.startLine >= range.endLine && next == null) {
        next = each;
      }
    }

    info.toMove = range;
    info.toMove2 = down ? next : prev;
    return true;
  }

  @Nullable
  private static GroovyPsiElement getElementToMove(GroovyFileBase file, int offset) {
    offset = CharArrayUtil.shiftForward(file.getText(), offset, " \t");
    PsiElement element = file.findElementAt(offset);
    final GrDocComment docComment = PsiTreeUtil.getParentOfType(element, GrDocComment.class);
    if (docComment != null) {
      final GrDocCommentOwner owner = docComment.getOwner();
      if (owner != null) {
        element = owner;
      }
    }
    if (element instanceof PsiComment) {
      element = PsiTreeUtil.nextVisibleLeaf(element);
    }

    return (GroovyPsiElement)PsiTreeUtil.findFirstParent(element, element11 -> isMoveable(element11));
  }

  private List<LineRange> allRanges(final GroovyPsiElement scope, final boolean stmtLevel, final boolean topLevel) {
    final ArrayList<LineRange> result = new ArrayList<>();
    scope.accept(new PsiRecursiveElementVisitor() {
      int lastStart = -1;

      private void addRange(int endLine) {
        if (lastStart >= 0) {
          result.add(new LineRange(lastStart, endLine));
        }
        lastStart = endLine;
      }

      @Override
      public void visitElement(PsiElement element) {
        if (stmtLevel && element instanceof GrCodeBlock) {
          final PsiElement lBrace = ((GrCodeBlock)element).getLBrace();
          if (nlsAfter(lBrace)) {
            assert lBrace != null;
            addRange(new LineRange(lBrace).endLine);
          }
          addChildRanges(((GrCodeBlock)element).getStatements());
          final PsiElement rBrace = ((GrCodeBlock)element).getRBrace();
          if (nlsAfter(rBrace)) {
            assert rBrace != null;
            final int endLine = new LineRange(rBrace).endLine;
            if (lastStart >= 0) {
              for (int i = lastStart + 1; i < endLine; i++) {
                addRange(i);
              }
            }
          }
        }
        else if (stmtLevel && element instanceof GrCaseSection) {
          final GrCaseLabel[] allLabels = ((GrCaseSection)element).getCaseLabels();
          final GrCaseLabel label = allLabels[0];
          if (nlsAfter(label)) {
            addRange(new LineRange(label).endLine);
          }
          addChildRanges(((GrCaseSection)element).getStatements());
        }
        else if (element instanceof GroovyFileBase) {
          addChildRanges(((GroovyFileBase)element).getTopStatements());
        }
        else if (!stmtLevel && !topLevel && element instanceof GrTypeDefinitionBody) {
          addChildRanges(((GrTypeDefinitionBody)element).getMemberDeclarations());
        }
        else {
          super.visitElement(element);
        }
      }

      private boolean shouldDigInside(GrTopStatement statement) {
        if (stmtLevel && (statement instanceof GrMethod || statement instanceof GrTypeDefinition)) {
          return false;
        }
        if (statement instanceof GrVariableDeclaration && !stmtLevel) {
          return false;
        }
        return true;
      }

      private void addChildRanges(GrTopStatement[] statements) {
        for (int i = 0; i < statements.length; i++) {
          GrTopStatement statement = statements[i];
          if (nlsAfter(statement)) {
            final LineRange range = getLineRange(statement);
            if ((i == 0 || isStatement(statements[i-1])) && isStatement(statement)) {
              for (int j = lastStart; j < range.startLine; j++) {
                addRange(j + 1);
              }
            }
            lastStart = range.startLine;
            if (shouldDigInside(statement)) {
              statement.accept(this);
            }
            addRange(range.endLine);
          }
        }
      }
    });
    return result;
  }

  private static boolean nlsAfter(@Nullable PsiElement element) {
    if (element == null) return false;

    PsiElement sibling = element;
    while (true) {
      sibling = PsiTreeUtil.nextLeaf(sibling);
      if (sibling == null) {
        return true; //eof
      }

      final String text = sibling.getText();
      if (text.contains("\n")) {
        return text.charAt(CharArrayUtil.shiftForward(text, 0, " \t")) == '\n';
      }

      if (!(sibling instanceof PsiComment) && !StringUtil.isEmptyOrSpaces(text) && !text.equals(";")) {
        return false;
      }

    }
  }

  private static boolean isMoveable(PsiElement element) {
    return isStatement(element) || isMemberDeclaration(element);
  }

  private static boolean isMemberDeclaration(PsiElement element) {
    return element instanceof GrMembersDeclaration || element instanceof GrTypeDefinition;
  }

  private static boolean isStatement(PsiElement element) {
    return element instanceof GrStatement && PsiUtil.isExpressionStatement(element);
  }

  private static LineRange getLineRange(GroovyPsiElement pivot) {
    if (pivot instanceof GrDocCommentOwner) {
      final GrDocComment comment = ((GrDocCommentOwner)pivot).getDocComment();
      if (comment != null) {
        return new LineRange(comment, pivot);
      }
    }


    return new LineRange(pivot);
  }

}
