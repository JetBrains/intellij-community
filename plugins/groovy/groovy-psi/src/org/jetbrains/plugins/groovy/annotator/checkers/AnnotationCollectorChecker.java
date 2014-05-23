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
import com.intellij.openapi.util.Condition;
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
    CustomAnnotationChecker.checkAnnotationArguments(holder, clazz, annotation.getClassReference(), attributes, false);

    return true;
  }

  private static boolean isInAliasDeclaration(GrAnnotation annotation) {
    final PsiElement parent = annotation.getParent();
    if (parent instanceof GrModifierList) {
      final GrAnnotation collector = ContainerUtil.find(((GrModifierList)parent).getRawAnnotations(), new Condition<GrAnnotation>() {
        @Override
        public boolean value(GrAnnotation annotation) {
          return GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(annotation.getQualifiedName());
        }
      });
      if (collector != null) {
        return true;
      }
    }

    return false;
  }
}
