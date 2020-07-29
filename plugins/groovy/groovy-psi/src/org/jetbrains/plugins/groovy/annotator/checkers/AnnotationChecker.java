// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrRemoveAnnotationIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public final class AnnotationChecker {

  public static void checkApplicability(@NotNull GrAnnotation annotation,
                                        @Nullable PsiAnnotationOwner owner,
                                        @NotNull AnnotationHolder holder,
                                        @NotNull PsiElement toHighlight) {
    final GrCodeReferenceElement ref = annotation.getClassReference();
    final PsiElement resolved = ref.resolve();

    if (resolved == null) return;
    assert resolved instanceof PsiClass;

    PsiClass anno = (PsiClass)resolved;
    String qname = anno.getQualifiedName();
    if (!anno.isAnnotationType() && GrAnnotationCollector.findAnnotationCollector(anno) == null) {
      if (qname != null) {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("class.is.not.annotation", qname)).range(toHighlight).create();
      }
      return;
    }

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkApplicability(holder, annotation)) return;
    }

    String description = CustomAnnotationChecker.checkAnnotationApplicable(annotation, owner);
    if (description != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, description).range(toHighlight).withFix(new GrRemoveAnnotationIntention()).create();
    }
  }

  public static @Nullable Pair<@Nullable PsiElement, @Nullable String> checkAnnotationArgumentList(@NotNull GrAnnotation annotation,
                                                                                                   @NotNull AnnotationHolder holder) {
    final PsiClass anno = ResolveUtil.resolveAnnotation(annotation);
    if (anno == null) return null;

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkArgumentList(holder, annotation)) return Pair.create(null, null);
    }

    return CustomAnnotationChecker.checkAnnotationArguments(
      anno, annotation.getParameterList().getAttributes(), true
    );
  }
}
