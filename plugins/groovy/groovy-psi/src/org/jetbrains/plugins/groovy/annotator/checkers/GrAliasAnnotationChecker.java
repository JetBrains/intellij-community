// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrAliasAnnotationChecker extends CustomAnnotationChecker {

  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final ArrayList<GrAnnotation> aliasedAnnotations = getAliasedAnnotations(annotation);
    if (aliasedAnnotations == null) {
      return false;
    }

    AliasedAnnotationHolder aliasedHolder = new AliasedAnnotationHolder(holder, annotation);
    AnnotationChecker checker = new AnnotationChecker(aliasedHolder);
    for (GrAnnotation aliased : aliasedAnnotations) {
      checker.checkApplicability(aliased, annotation.getOwner());
    }

    return true;
  }

  @Nullable
  private static ArrayList<GrAnnotation> getAliasedAnnotations(GrAnnotation annotation) {
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) return null;

    final ArrayList<GrAnnotation> aliasedAnnotations = ContainerUtil.newArrayList();
    GrAnnotationCollector.collectAnnotations(aliasedAnnotations, annotation, annotationCollector);
    return aliasedAnnotations;
  }

  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final PsiAnnotation annotationCollector = GrAnnotationCollector.findAnnotationCollector(annotation);
    if (annotationCollector == null) {
      return false;
    }

    final ArrayList<GrAnnotation> annotations = ContainerUtil.newArrayList();
    final Set<String> usedAttributes = GrAnnotationCollector.collectAnnotations(annotations, annotation, annotationCollector);

    AliasedAnnotationHolder aliasedHolder = new AliasedAnnotationHolder(holder, annotation);
    AnnotationChecker checker = new AnnotationChecker(aliasedHolder);
    for (GrAnnotation aliased : annotations) {
      if (checker.checkAnnotationArgumentList(aliased)) return true;
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
