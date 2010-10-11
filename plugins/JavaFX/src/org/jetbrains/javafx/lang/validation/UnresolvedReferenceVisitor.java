package org.jetbrains.javafx.lang.validation;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.HashSet;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceExpression;
import org.jetbrains.javafx.lang.psi.JavaFxThisReferenceExpression;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class UnresolvedReferenceVisitor extends JavaFxAnnotatingVisitor {
  private static final Set<String> MAGIC_CONSTANTS = new HashSet<String>();

  static {
    MAGIC_CONSTANTS.add("__DIR__");
    MAGIC_CONSTANTS.add("__FILE__");
    MAGIC_CONSTANTS.add("__PROFILE__");
  }

  @Override
  public void visitReferenceExpression(JavaFxReferenceExpression node) {
    final String text = node.getText();
    if (MAGIC_CONSTANTS.contains(text)) {
      return;
    }
    visitReferenceElement(node);
  }

  @Override
  public void visitThisExpression(JavaFxThisReferenceExpression node) {
    visitReferenceExpression(node);
  }

  @Override
  public void visitReferenceElement(JavaFxReferenceElement node) {
    final PsiReference reference = node.getReference();
    if (reference != null) {
      if (!checkReference(reference)) {
        final TextRange textRange = calcRangeForReferences(reference);
        final Annotation annotation =
          getHolder().createErrorAnnotation(textRange, JavaFxBundle.message("javafx.unresolved.symbol.$0", reference.getCanonicalText()));
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }

  private static TextRange calcRangeForReferences(final PsiReference reference) {
    final TextRange elementRange = reference.getElement().getTextRange();
    final TextRange textRange = reference.getRangeInElement();

    return new TextRange(elementRange.getStartOffset() + textRange.getStartOffset(),
                         elementRange.getStartOffset() + textRange.getEndOffset());
  }

  private static boolean checkReference(final PsiReference reference) {
    final PsiElement resolveResult = reference.resolve();
    return (resolveResult != null && resolveResult.isValid());
  }
}
