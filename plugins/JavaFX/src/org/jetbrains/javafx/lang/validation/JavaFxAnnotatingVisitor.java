package org.jetbrains.javafx.lang.validation;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
abstract public class JavaFxAnnotatingVisitor extends JavaFxElementVisitor {
  private AnnotationHolder myHolder;

  public AnnotationHolder getHolder() {
    return myHolder;
  }

  public void setHolder(AnnotationHolder holder) {
    myHolder = holder;
  }

  public synchronized void annotateElement(final PsiElement psiElement, final AnnotationHolder holder) {
    myHolder = holder;
    try {
      psiElement.accept(this);
    }
    finally {
      myHolder = null;
    }
  }

  protected Annotation markError(PsiElement element, String message) {
    return myHolder.createErrorAnnotation(element, message);
  }
}
