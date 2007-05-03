package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.LightweightHint;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.OuterImportsActionCreator;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.bodies.GrClassBody;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;

/**
 * @author ven
 */
public class GroovyAnnotator implements Annotator {
  private GroovyAnnotator() {
  }

  public static final GroovyAnnotator INSTANCE = new GroovyAnnotator();

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (element instanceof GrTypeOrPackageReferenceElement) {
      checkReferenceElement(element, holder, (GrTypeOrPackageReferenceElement) element);
    } else if (element instanceof GrTypeDefinition) {
      checkTypeDefinition(holder, (GrTypeDefinition) element);
    }
  }

  private void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.getParent() instanceof GrClassBody) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), "Inner classes are not supported in Groovy");
    }
  }

  private void checkReferenceElement(PsiElement element, AnnotationHolder holder, GrTypeOrPackageReferenceElement refElement) {
    if (!refElement.isSoft() && refElement.getReferenceName() != null) {
      final PsiElement resolved = refElement.resolve();
      if (resolved == null) {
        String message = GroovyBundle.message("cannot.resolve") + " " + refElement.getReferenceName();
        final Annotation annotation = holder.createErrorAnnotation(element, message);

        // Register quickfix
        for (IntentionAction action : OuterImportsActionCreator.getOuterImportFixes(refElement, annotation, refElement.getProject())) {
          annotation.registerFix(action);
        }

        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }
}

