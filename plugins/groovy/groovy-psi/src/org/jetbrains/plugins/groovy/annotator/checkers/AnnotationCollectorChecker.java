// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Medvdedev Max
 */

public class AnnotationCollectorChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    return isInAliasDeclaration(annotation);
  }

  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (!isInAliasDeclaration(annotation)) return false;

    final PsiClass clazz = (PsiClass)annotation.getClassReference().resolve();
    if (clazz == null) return true;
    final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    Pair<PsiElement, @InspectionMessage String> r = CustomAnnotationChecker.checkAnnotationArguments(clazz, attributes, false);
    if (r != null && r.getFirst() != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, r.getSecond()).range(r.getFirst()).create();
    }

    return true;
  }

  private static boolean isInAliasDeclaration(GrAnnotation annotation) {
    final PsiElement parent = annotation.getParent();
    if (parent instanceof GrModifierList) {
      final GrAnnotation collector = ContainerUtil.find(((GrModifierList)parent).getRawAnnotations(),
                                                        annotation1 -> GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(
                                                          annotation1.getQualifiedName()));
      if (collector != null) {
        return true;
      }
    }

    return false;
  }
}
