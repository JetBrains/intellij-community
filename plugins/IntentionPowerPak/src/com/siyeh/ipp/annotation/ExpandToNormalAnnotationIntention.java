/*
 * Copyright 2010-2014 Bas Leijdekkers
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
package com.siyeh.ipp.annotation;

import com.intellij.psi.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExpandToNormalAnnotationIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ExpandToNormalAnnotationPredicate();
  }

  public static String buildReplacementText(PsiAnnotationParameterList annotationParameterList) {
    final StringBuilder text = new StringBuilder();
    for (PsiNameValuePair nameValuePair : annotationParameterList.getAttributes()) {
      if (text.length() != 0) {
        text.append(',');
      }
      final String name = nameValuePair.getName();
      text.append(name != null ? name : "value").append('=');
      final PsiAnnotationMemberValue value = nameValuePair.getValue();
      if (value != null) {
        text.append(value.getText());
      }
    }
    return text.toString();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAnnotationParameterList annotationParameterList = (PsiAnnotationParameterList)element.getParent();
    final String text = buildReplacementText(annotationParameterList);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(annotationParameterList.getProject());
    final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text +" )", element);
    annotationParameterList.replace(newAnnotation.getParameterList());
  }
}
