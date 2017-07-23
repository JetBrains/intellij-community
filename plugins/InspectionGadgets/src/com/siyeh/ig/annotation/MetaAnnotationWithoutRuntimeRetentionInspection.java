/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class MetaAnnotationWithoutRuntimeRetentionInspection extends BaseJavaBatchLocalInspectionTool {
  private static Collection<String> ourAnnotations = new HashSet<>();
  static {
    ourAnnotations.add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST);
    ourAnnotations.add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST);
    ourAnnotations.add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST);
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!aClass.isAnnotationType()) {
      return null;
    }
    if (MetaAnnotationUtil.isMetaAnnotated(aClass, ourAnnotations)) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
      if (annotation == null) {
        String runtimeRef = StringUtil.getQualifiedName("java.lang.annotation.RetentionPolicy", "RUNTIME");
        PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(aClass.getProject())
          .createAnnotationFromText("@Retention(" + runtimeRef + ")", aClass);
        AddAnnotationPsiFix annotationPsiFix = new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION,
                                                                       aClass,
                                                                       newAnnotation.getParameterList().getAttributes());
        ProblemDescriptor descriptor =
          manager.createProblemDescriptor(ObjectUtils.notNull(aClass.getNameIdentifier(), aClass),
                                          aClass.getName() + " should have @Retention(RetentionPolicy.RUNTIME)",
                                          annotationPsiFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        return new ProblemDescriptor[] {descriptor};
      }
      else {
        PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue("value");
        if (attributeValue == null || !attributeValue.getText().contains("RUNTIME")) {
          ProblemDescriptor descriptor =
            manager.createProblemDescriptor(ObjectUtils.notNull(aClass.getNameIdentifier(), aClass),
                                            aClass.getName() + "Meta annotation should have @Retention(RetentionPolicy.RUNTIME)",
                                            (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
          return new ProblemDescriptor[] {descriptor};
        }
      }
    }
    return null;
  }
}
