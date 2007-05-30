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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.OuterImportsActionCreator;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.bodies.GrClassBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class GroovyAnnotator implements Annotator {
  private GroovyAnnotator() {
  }

  public static final GroovyAnnotator INSTANCE = new GroovyAnnotator();

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (element instanceof GrTypeOrPackageReferenceElement) {
      checkReferenceElement(holder, (GrTypeOrPackageReferenceElement) element);
    } else if (element instanceof GrReferenceExpression) {
      checkReferenceExpression(holder, (GrReferenceExpression) element);
    } else if (element instanceof GrTypeDefinition) {
      checkTypeDefinition(holder, (GrTypeDefinition) element);
    }
  }

  private void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.getParent() instanceof GrClassBody) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), "Inner classes are not supported in Groovy");
    }
  }

  private void checkReferenceExpression(AnnotationHolder holder, final GrReferenceExpression refExpr) {
    GroovyResolveResult resolveResult = refExpr.advancedResolve();
    PsiElement element = resolveResult.getElement();
    if (element != null) {
      if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", refExpr.getReferenceName());
        holder.createWarningAnnotation(refExpr, message);
      } else if (element instanceof PsiMethod) {
        /*PsiType[] argumentTypes = PsiUtil.getArgumentTypes(refExpr);
        if (argumentTypes != null && !PsiUtil.isApplicable(argumentTypes, (PsiMethod)element)) {
          //todo more specific error message
          String message = GroovyBundle.message("cannot.apply.method", refExpr.getReferenceName());
          holder.createWarningAnnotation(refExpr, message);
        }*/
      }
    } else {
      if (refExpr.getQualifierExpression() == null) {
        GrMethod method = PsiTreeUtil.getParentOfType(refExpr, GrMethod.class); //todo for static fields as well
        if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
          Annotation annotation = holder.createErrorAnnotation(refExpr, GroovyBundle.message("cannot.resolve", refExpr.getReferenceName()));
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        } else {
          if (refExpr.getParent() instanceof GrReferenceExpression) {
            holder.createWarningAnnotation(refExpr, GroovyBundle.message("cannot.resolve", refExpr.getReferenceName()));
          }
        }
      }
    }
  }

  private void checkReferenceElement(AnnotationHolder holder, final GrTypeOrPackageReferenceElement refElement) {
    if (refElement.getReferenceName() != null) {
      GroovyResolveResult resolveResult = refElement.advancedResolve();
      final PsiElement resolved = resolveResult.getElement();
      if (resolved == null) {
        String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());

        // Register quickfix
        final Annotation annotation = holder.createErrorAnnotation(refElement, message);
        final IntentionAction[] actions = OuterImportsActionCreator.getOuterImportFixes(refElement, annotation, refElement.getProject());
        for (IntentionAction action : actions) {
          annotation.registerFix(action);
        }
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      } else if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", refElement.getReferenceName());
        holder.createErrorAnnotation(refElement, message);
      }
    }
  }
}

