package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrReferenceElement;

/**
 * @author ven
 */
public class GroovyAnnotator implements Annotator {
  private GroovyAnnotator() {}

  public static final GroovyAnnotator INSTANCE = new GroovyAnnotator();

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (element instanceof GrReferenceElement) {
      GrReferenceElement refElement = (GrReferenceElement) element;
      if (!refElement.isSoft() && refElement.getReferenceName() != null) {
        PsiElement resolved = refElement.resolve();
        if (resolved == null) {
          String message = String.format("Cannot resolve symbol {0}", refElement.getReferenceName());
          holder.createErrorAnnotation(element, message);
        }
      }
    }
  }
}
