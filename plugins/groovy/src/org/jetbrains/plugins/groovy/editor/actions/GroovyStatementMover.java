// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

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

    return (GroovyPsiElement)PsiTreeUtil.findFirstParent(element, element11 -> isMovable(element11));
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
      public void visitElement(@NotNull PsiElement element) {
        if (stmtLevel && element instanceof GrCodeBlock) {
          final PsiElement lBrace = ((GrCodeBlock)element).getLBrace();
          if (nlsAfter(lBrace)) {
            addRange(new LineRange(lBrace).endLine);
          }
          addChildRanges(((GrCodeBlock)element).getStatements());
          final PsiElement rBrace = ((GrCodeBlock)element).getRBrace();
          if (nlsAfter(rBrace)) {
            final int endLine = new LineRange(rBrace).endLine;
            if (lastStart >= 0) {
              for (int i = lastStart + 1; i < endLine; i++) {
                addRange(i);
              }
            }
          }
        }
        else if (stmtLevel && element instanceof GrCaseSection) {
          PsiElement delimiter = ((GrCaseSection)element).getColon();
          if (delimiter != null) {
            if (nlsAfter(delimiter)) {
              addRange(new LineRange(element.getFirstChild(), delimiter).endLine);
            }
            addChildRanges(((GrCaseSection)element).getStatements());
          } else {
            var statements = ((GrCaseSection)element).getStatements();
            if (statements.length == 1 && statements[0] instanceof GrBlockStatement) {
              GrOpenBlock block = ((GrBlockStatement)statements[0]).getBlock();
              if (nlsAfter(block.getLBrace())) {
                addRange(new LineRange(element.getFirstChild(), block.getLBrace()).endLine);
              }
              addChildRanges(block.getStatements());
            }
          }
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

      private boolean shouldDigInside(GroovyPsiElement statement) {
        if (stmtLevel && (statement instanceof GrMethod || statement instanceof GrTypeDefinition)) {
          return false;
        }
        if (statement instanceof GrVariableDeclaration && !stmtLevel) {
          return false;
        }
        return true;
      }

      private void addChildRanges(GroovyPsiElement[] statements) {
        for (int i = 0; i < statements.length; i++) {
          GroovyPsiElement statement = statements[i];
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

  private static boolean isMovable(PsiElement element) {
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
    else if (pivot instanceof GrVariableDeclaration && pivot.getParent() instanceof GrTypeDefinitionBody) {
      GrVariable[] variables = ((GrVariableDeclaration)pivot).getVariables();
      if (variables.length > 0) {
        GrVariable variable = variables[0];
        if (variable instanceof GrField) {
          GrDocComment comment = ((GrField)variable).getDocComment();
          if (comment != null) {
            return new LineRange(comment, pivot);
          }
        }
      }
    }


    return new LineRange(pivot);
  }

}
