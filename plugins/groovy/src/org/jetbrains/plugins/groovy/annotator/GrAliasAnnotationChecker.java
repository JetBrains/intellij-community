/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class GrAliasAnnotationChecker extends CustomAnnotationChecker {
  @Nullable
  private static GrTypeDefinition resolveAlias(@NotNull GrAnnotation annotation) {
    final GrCodeReferenceElement ref = annotation.getClassReference();

    final PsiElement resolved = ref.resolve();
    if (GrAnnotationCollector.findAnnotationCollector(resolved) != null) {
      assert resolved instanceof GrTypeDefinition;
      return ((GrTypeDefinition)(resolved));
    }


    return null;
  }

  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {

    final GrCodeReferenceElement ref = annotation.getClassReference();

    final GrTypeDefinition resolved = resolveAlias(annotation);
    if (resolved == null) {
      return false;
    }

    final GrModifierList list = resolved.getModifierList();
    assert list != null;
    for (GrAnnotation anno : list.getRawAnnotations()) {
      if (GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(anno.getQualifiedName())) continue;
      final String description = CustomAnnotationChecker.isAnnotationApplicable(anno, annotation.getParent());
      if (description != null) {
        holder.createErrorAnnotation(ref, description);
      }
    }

    return true;
  }

  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (resolveAlias(annotation) != null) {
      return true;
    }


    return false;
  }
}
