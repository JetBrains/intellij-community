/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.application.ApplicationManager;
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

  private void checkReferenceElement(final PsiElement element, AnnotationHolder holder, final GrTypeOrPackageReferenceElement refElement) {
    if (!refElement.isSoft() && refElement.getReferenceName() != null) {
      final PsiElement resolved = refElement.resolve();
      if (resolved == null) {
        String message = GroovyBundle.message("cannot.resolve") + " " + refElement.getReferenceName();

        // Register quickfix
        final Annotation annotation = holder.createErrorAnnotation(element, message);
        final IntentionAction[] actions = OuterImportsActionCreator.getOuterImportFixes(refElement, annotation, refElement.getProject());
        for (IntentionAction action : actions) {
          annotation.registerFix(action);
        }
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }
}

