package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiElement;
import com.intellij.codeInspection.ProblemHighlightType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.bodies.GrClassBody;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrReferenceElement;

/**
 * @author ven
 */
public class GroovyAnnotator implements Annotator {
  private GroovyAnnotator() {}

  public static final GroovyAnnotator INSTANCE = new GroovyAnnotator();

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (element instanceof GrReferenceElement) {
      checkReferenceElement(element, holder, (GrReferenceElement) element);
    } else if (element instanceof GrTypeDefinition) {
      checkTypeDefinition(holder, (GrTypeDefinition) element);
    }
  }

  private void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.getParent() instanceof GrClassBody) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), "Inner classes are not supported in Groovy");
    }
  }

  private void checkReferenceElement(PsiElement element, AnnotationHolder holder, GrReferenceElement refElement) {
    if (!refElement.isSoft() && refElement.getReferenceName() != null) {
        PsiElement resolved = refElement.resolve();
        if (resolved == null) {
          String message = "Cannot resolve symbol " + refElement.getReferenceName();
          Annotation annotation = holder.createErrorAnnotation(element, message);
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
  }
}

