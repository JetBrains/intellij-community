// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class MetaAnnotationWithoutRuntimeRetentionInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Collection<String> ourAnnotations = new HashSet<>();
  static {
    ourAnnotations.add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST);
    ourAnnotations.add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED);
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
