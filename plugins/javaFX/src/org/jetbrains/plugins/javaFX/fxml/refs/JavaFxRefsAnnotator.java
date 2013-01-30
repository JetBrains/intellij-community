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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.util.Collections;

/**
 * User: anna
 */
public class JavaFxRefsAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final PsiFile containingFile = element.getContainingFile();
    if (!JavaFxFileTypeFactory.isFxml(containingFile)) return;
    if (element instanceof XmlAttributeValue) {
      final PsiReference[] references = element.getReferences();
      for (PsiReference reference : references) {
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiMember) {
          if (!((PsiMember)resolve).hasModifierProperty(PsiModifier.PUBLIC) &&
              !AnnotationUtil.isAnnotated((PsiMember)resolve, Collections.singleton(JavaFxCommonClassNames.JAVAFX_FXML_ANNOTATION))) {
            final String symbolPresentation = "'" + SymbolPresentationUtil.getSymbolPresentableText(resolve) + "'";
            final Annotation annotation = holder.createErrorAnnotation(element, 
                                                                       symbolPresentation + (resolve instanceof PsiClass ? " should be public" : " should be public or annotated with @FXML"));
            if (!(resolve instanceof PsiClass)) {
              annotation.registerUniversalFix(new AddAnnotationFix(JavaFxCommonClassNames.JAVAFX_FXML_ANNOTATION, (PsiMember)resolve, ArrayUtil.EMPTY_STRING_ARRAY), null, null);
            }
          }
        }
      }
    }
  }
}
