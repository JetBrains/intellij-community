// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class TypeCheckedAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final GrCodeReferenceElement classReference = annotation.getClassReference();
    PsiElement resolved = classReference.resolve();
    if (!(resolved instanceof PsiClass &&
          GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKED.equals(((PsiClass)resolved).getQualifiedName()))) {
      return false;
    }

    String sdkVersion = GroovyConfigUtils.getInstance().getSDKVersion(annotation);
    if (!("2.1".equals(sdkVersion) ||
          "2.1.0".equals(sdkVersion))) return false;

    GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    Pair<PsiElement, @InspectionMessage String> r = checkAnnotationArguments((PsiClass)resolved, attributes, false);
    if (r != null && r.getFirst() != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, r.getSecond()).range(r.getFirst()).create();
    }

    return true;
  }
}
