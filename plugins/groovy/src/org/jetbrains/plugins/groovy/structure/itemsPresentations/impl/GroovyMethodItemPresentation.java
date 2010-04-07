/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyMethodItemPresentation extends GroovyItemPresentation {
  private final boolean isInherit;
  private final NotNullLazyValue<String> myPresentableText = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      final PsiMethod method = (PsiMethod)myElement;
      StringBuilder presentableText = new StringBuilder();
      presentableText.append(method.getName());
      presentableText.append(" ");

      PsiParameterList paramList = method.getParameterList();
      PsiParameter[] parameters = paramList.getParameters();

      presentableText.append("(");
      for (int i = 0; i < parameters.length; i++) {
        if (i > 0) presentableText.append(", ");
        presentableText.append(parameters[i].getType().getPresentableText());
      }
      presentableText.append(")");

      PsiType returnType = method.getReturnType();

      if (returnType != null) {
        presentableText.append(":");
        presentableText.append(returnType.getPresentableText());
      }

      return presentableText.toString();
    }
  };

  public GroovyMethodItemPresentation(PsiMethod myElement, boolean isInherit) {
    super(myElement);
    this.isInherit = isInherit;
  }

  public String getPresentableText() {
    return myPresentableText.getValue();
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return isInherit ? CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES : null;
  }
}
