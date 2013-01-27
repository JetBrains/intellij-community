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

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;

/**
 * @author Max Medvedev
 */
public abstract class CustomAnnotationChecker {
  public static final ExtensionPointName<CustomAnnotationChecker> EP_NAME = ExtensionPointName.create("org.intellij.groovy.customAnnotationChecker");

  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {return false;}

  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {return false;}

  @Nullable
  public static String isAnnotationApplicable(@NotNull GrAnnotation annotation, final PsiElement parent) {
    PsiElement owner = parent.getParent();

    final PsiElement ownerToUse = parent instanceof PsiModifierList ? owner : parent;

    String[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
    if (elementTypeFields != null && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, false, elementTypeFields)) {
      final String annotationTargetText = JavaErrorMessages.message("annotation.target." + elementTypeFields[0]);
      GrCodeReferenceElement ref = annotation.getClassReference();
      return JavaErrorMessages.message("annotation.not.applicable", ref.getText(), annotationTargetText);
    }

    return null;
  }
}
