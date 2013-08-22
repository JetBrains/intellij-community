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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrAliasAnnotationChecker extends CustomAnnotationChecker {

  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) {
      return false;
    }

    final GrCodeReferenceElement ref = annotation.getClassReference();

    final ArrayList<GrAnnotation> aliasedAnnotations = ContainerUtil.newArrayList();
    GrAnnotationCollector.collectAnnotations(aliasedAnnotations, annotation, annotationCollector);

    for (GrAnnotation anno : aliasedAnnotations) {
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
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) {
      return false;
    }

    final ArrayList<GrAnnotation> annotations = ContainerUtil.newArrayList();
    final Set<String> usedAttributes = GrAnnotationCollector.collectAnnotations(annotations, annotation, annotationCollector);

    final GrCodeReferenceElement ref = annotation.getClassReference();

    Map<PsiElement, String> map = ContainerUtil.newHashMap();
    for (GrAnnotation aliased : annotations) {
      final PsiClass clazz = (PsiClass)aliased.getClassReference().resolve();
      assert clazz != null;
      checkAnnotationArguments(map, clazz, ref, aliased.getParameterList().getAttributes(), true);
    }

    for (Map.Entry<PsiElement, String> entry : map.entrySet()) {
      final PsiElement key = entry.getKey();
      final String value = entry.getValue();
      if (PsiTreeUtil.isAncestor(annotation, key, true)) {
        holder.createErrorAnnotation(key, value);
      }
      else {
        holder.createErrorAnnotation(ref, value);
      }
    }

    final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    final String aliasQName = annotation.getQualifiedName();

    if (attributes.length == 1 && attributes[0].getNameIdentifierGroovy() == null && !usedAttributes.contains("value")) {
      holder.createErrorAnnotation(attributes[0], GroovyBundle.message("at.interface.0.does.not.contain.attribute", aliasQName, "value"));
    }

    for (GrAnnotationNameValuePair pair : attributes) {
      final PsiElement nameIdentifier = pair.getNameIdentifierGroovy();
      if (nameIdentifier != null && !usedAttributes.contains(pair.getName())) {
        holder.createErrorAnnotation(nameIdentifier, GroovyBundle.message("at.interface.0.does.not.contain.attribute", aliasQName, pair.getName()));
      }
    }
    return true;
  }
}
