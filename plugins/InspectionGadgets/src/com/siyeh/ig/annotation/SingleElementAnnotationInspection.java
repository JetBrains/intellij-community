/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
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
package com.siyeh.ig.annotation;

import com.intellij.codeInsight.daemon.impl.quickfix.AddAnnotationAttributeNameFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateAnnotationMethodFromUsageFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class SingleElementAnnotationInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("single.element.annotation.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpandAnnotationVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExpandAnnotationFix();
  }

  private static class ExpandAnnotationFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("single.element.annotation.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("single.element.annotation.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiNameValuePair annotationParameter = (PsiNameValuePair)descriptor.getPsiElement();
      final String text = buildReplacementText(annotationParameter);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(annotationParameter.getProject());
      final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text + " )", annotationParameter);
      annotationParameter.replace(newAnnotation.getParameterList().getAttributes()[0]);
    }

    private static String buildReplacementText(@NotNull PsiNameValuePair annotationParameter) {
      final PsiAnnotationMemberValue value = annotationParameter.getValue();
      return PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + "=" + (value != null ? value.getText() : "");
    }
  }

  private static class ExpandAnnotationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitNameValuePair(PsiNameValuePair pair) {
      super.visitNameValuePair(pair);

      if (pair.getName() == null) {
        final PsiElement parent = pair.getParent();
        if (parent instanceof PsiAnnotationParameterList) {
          final Set<String> usedNames = AddAnnotationAttributeNameFix.getUsedAttributeNames((PsiAnnotationParameterList)parent);
          if (!usedNames.contains(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
            final PsiReference reference = pair.getReference();
            if (reference != null) {
              final PsiElement resolved = reference.resolve();
              if (resolved instanceof PsiMethod) {
                final PsiAnnotationMemberValue value = pair.getValue();
                final PsiType valueType = CreateAnnotationMethodFromUsageFix.getAnnotationValueType(value);
                if (AddAnnotationAttributeNameFix.isCompatibleReturnType((PsiMethod)resolved, valueType)) {
                  registerError(pair);
                }
              }
            }
          }
        }
      }
    }
  }
}
