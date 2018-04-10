/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;

public class NamedArgumentReference extends PsiPolyVariantReferenceBase<GrArgumentLabel> {

  private final @NotNull PsiElement myNavigationElement;
  private final @NotNull PsiSubstitutor mySubstitutor;

  public NamedArgumentReference(@NotNull GrArgumentLabel element,
                                @NotNull PsiElement navigationElement,
                                @NotNull PsiSubstitutor substitutor) {
    super(element);
    myNavigationElement = navigationElement;
    mySubstitutor = substitutor;
  }

  @Override
  public PsiElement resolve() {
    return myNavigationElement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element == myNavigationElement) return getElement();
    return super.bindToElement(element);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();

    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolved;
      final String oldName = getElement().getName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtilBase.isSimplePropertySetter(method)) {
          final String newPropertyName = PropertyUtilBase.getPropertyName(newElementName);
          if (newPropertyName != null) {
            newElementName = newPropertyName;
          }
        }
      }
    }

    return super.handleElementRename(newElementName);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return new GroovyResolveResult[]{new GroovyResolveResultImpl(myNavigationElement, null, null, mySubstitutor, true, true)};
  }
}
