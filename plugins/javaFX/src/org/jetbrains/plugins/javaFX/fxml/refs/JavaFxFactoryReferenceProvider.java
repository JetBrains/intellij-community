// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

class JavaFxFactoryReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull ProcessingContext context) {
    final XmlAttributeValue attributeValue = (XmlAttributeValue)element;
    return new PsiReference[] {new JavaFXFactoryReference(attributeValue)};
  }

  private static class JavaFXFactoryReference extends PsiReferenceBase<XmlAttributeValue> {
    JavaFXFactoryReference(XmlAttributeValue attributeValue) {
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
             method.getParameterList().isEmpty() &&
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
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
  }
}
