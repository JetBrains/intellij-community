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

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
* User: anna
*/
class JavaFxFactoryReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull ProcessingContext context) {
    final XmlAttributeValue attributeValue = (XmlAttributeValue)element;
    return new PsiReference[] {new JavaFXFactoryReference(attributeValue)};
  }

  private static class JavaFXFactoryReference extends PsiReferenceBase<XmlAttributeValue> {
    public JavaFXFactoryReference(XmlAttributeValue attributeValue) {
      super(attributeValue);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      final PsiClass psiClass = JavaFxPsiUtil.getTagClass(getElement());
      if (psiClass != null) {
        final PsiMethod[] psiMethods = psiClass.findMethodsByName(getElement().getValue(), false);
        for (PsiMethod method : psiMethods) {
          if (isFactoryMethod(method)) {
            return method;
          }
        }
      }
      return null;
    }

    private static boolean isFactoryMethod(PsiMethod method) {
      return method.hasModifierProperty(PsiModifier.STATIC) &&
             method.getParameterList().getParametersCount() == 0 &&
             !PsiType.VOID.equals(method.getReturnType());
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final PsiClass psiClass = JavaFxPsiUtil.getTagClass(getElement());
      if (psiClass != null) {
        final List<PsiMethod> methods = new ArrayList<>();
        for (PsiMethod method : psiClass.getMethods()) {
          if (isFactoryMethod(method)) {
            methods.add(method);
          }
        }
        return ArrayUtil.toObjectArray(methods);
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }
}
