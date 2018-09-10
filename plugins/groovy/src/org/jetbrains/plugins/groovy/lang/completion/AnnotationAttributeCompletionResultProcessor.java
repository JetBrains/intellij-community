// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;

import java.util.ArrayList;
import java.util.Set;

public class AnnotationAttributeCompletionResultProcessor {
  private final GrAnnotation myAnnotation;

  public AnnotationAttributeCompletionResultProcessor(@NotNull GrAnnotation annotation) {
    myAnnotation = annotation;
  }

  public void process(@NotNull Consumer<LookupElement> consumer, @NotNull PrefixMatcher matcher) {
    GrCodeReferenceElement ref = myAnnotation.getClassReference();
    PsiElement resolved = ref.resolve();

    if (resolved instanceof PsiClass) {
      final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved);

      if (annotationCollector != null) {
        final ArrayList<GrAnnotation> annotations = ContainerUtil.newArrayList();
        GrAnnotationCollector.collectAnnotations(annotations, myAnnotation, annotationCollector);

        Set<String> usedNames = ContainerUtil.newHashSet();
        for (GrAnnotation annotation : annotations) {
          final PsiElement resolvedAliased = annotation.getClassReference().resolve();
          if (resolvedAliased instanceof PsiClass && ((PsiClass)resolvedAliased).isAnnotationType()) {
            for (PsiMethod method : ((PsiClass)resolvedAliased).getMethods()) {
              if (usedNames.add(method.getName())) {
                for (LookupElement element : GroovyCompletionUtil
                  .createLookupElements(new ElementResolveResult<>(method), false, matcher, null)) {
                  consumer.consume(element);
                }
              }
            }
          }
        }
      }
      else if (((PsiClass)resolved).isAnnotationType()) {
        for (PsiMethod method : ((PsiClass)resolved).getMethods()) {
          for (LookupElement element : GroovyCompletionUtil
            .createLookupElements(new ElementResolveResult<>(method), false, matcher, null)) {
            consumer.consume(element);
          }
        }
      }
    }
  }

}
