package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyManyStatementsSurrounder implements Surrounder {

//  public boolean isApplicable(@NotNull PsiElement[] elements) {
////    for (PsiElement element : elements) {
////      if (!isApplicable(element)) {
////        return false;
////      }
////    }
////    return true;
//    return isStatements(elements);
//  }

  public boolean isStatements(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof GrStatement)) {
        return false;
      }
    }
    return true;
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    if (elements.length == 0) return false;
    if (elements.length == 1) return elements[0] instanceof GrStatement;
    return isStatements(elements);
  }

  protected String getListElementsTemplateAsString(PsiElement... elements) {
    StringBuffer result = new StringBuffer();
    for (PsiElement element : elements) {
      result.append(element.getText());
      result.append("\n");
    }
    return result.toString();
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

    final GroovyPsiElement newStmt = doSurroundElements(elements);
    assert newStmt != null;

    PsiElement element1 = elements[0];
    ASTNode parentNode = element1.getParent().getNode();


    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (i == 0) {
        parentNode.replaceChild(element1.getNode(), newStmt.getNode());
      } else {
        assert parentNode == element.getParent().getNode();
        parentNode.removeChild(element.getNode());
      }
    }

    final TextRange range = newStmt.getTextRange();
    final TextRange selectionRange = getSurroundSelectionRange(newStmt);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(newStmt.getContainingFile());
    final RangeMarker marker = document.createRangeMarker(selectionRange);
    newStmt.getManager().getCodeStyleManager().reformatText(newStmt.getContainingFile(), range.getStartOffset(), range.getEndOffset());
    return new TextRange(marker.getStartOffset(), marker.getEndOffset());
  }

  protected void addStatements(GrCodeBlock block, PsiElement[] elements) throws IncorrectOperationException {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      final GrStatement statement = (GrStatement) element;
      block.addStatementBefore((GrStatement) statement.copy(), null);
    }
  }

  protected abstract GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException;

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);

//  protected abstract boolean isApplicable(PsiElement element);
}