/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
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

/**
 * Created by Max Medvedev on 25/03/14
 */
public class AnnotationChecker {
  private final AnnotationHolder myHolder;

  public AnnotationChecker(@NotNull AnnotationHolder holder) {
    myHolder = holder;
  }

  public void checkApplicability(@NotNull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner) {
    final GrCodeReferenceElement ref = annotation.getClassReference();
    final PsiElement resolved = ref.resolve();

    if (resolved == null) return;
    assert resolved instanceof PsiClass;

    PsiClass anno = (PsiClass)resolved;
    String qname = anno.getQualifiedName();
    if (!anno.isAnnotationType() && GrAnnotationCollector.findAnnotationCollector(anno) == null) {
      if (qname != null) {
        myHolder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.annotation", qname));
      }
      return;
    }

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkApplicability(myHolder, annotation)) return;
    }

    String description = CustomAnnotationChecker.isAnnotationApplicable(annotation, owner);
    if (description != null) {
      myHolder.createErrorAnnotation(ref, description).registerFix(new GrRemoveAnnotationIntention());
    }
  }

  public void checkAnnotationArgumentList(@NotNull GrAnnotation annotation) {
    final PsiClass anno = ResolveUtil.resolveAnnotation(annotation);
    if (anno == null) return;

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkArgumentList(myHolder, annotation)) return;
    }

    CustomAnnotationChecker.checkAnnotationArguments(myHolder, anno, annotation.getClassReference(), annotation.getParameterList().getAttributes(), true);
  }
}
