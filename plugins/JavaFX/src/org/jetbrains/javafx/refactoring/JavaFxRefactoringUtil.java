package org.jetbrains.javafx.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxRefactoringUtil {
  private JavaFxRefactoringUtil() {
  }

  @Nullable
  public static JavaFxValueExpression findExpressionInRange(final PsiFile file, final int startOffset, final int endOffset) {
    if (startOffset > endOffset) {
      return null;
    }
    PsiElement element1 = file.findElementAt(startOffset);
    while (element1 instanceof PsiWhiteSpace) {
      element1 = element1.getNextSibling();
    }
    PsiElement element2 = file.findElementAt(endOffset);
    while (element2 instanceof PsiWhiteSpace) {
      element2 = element2.getPrevSibling();
    }
    final JavaFxValueExpression expression =
      PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, JavaFxValueExpression.class);
    if (expression == null || expression.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return expression;
  }
}
