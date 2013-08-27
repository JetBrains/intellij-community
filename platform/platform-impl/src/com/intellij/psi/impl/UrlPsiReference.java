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
package com.intellij.psi.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;

public class UrlPsiReference extends PsiReferenceBase<PsiElement> {
  public UrlPsiReference(PsiElement element) {
    super(element);
  }

  public PsiElement resolve() {
    return new FakePsiElement() {
      public PsiElement getParent() {
        return getElement();
      }

      @Override
      public String getName() {
        return getValue();
      }

      @Override
      public void navigate(boolean requestFocus) {
        BrowserUtil.launchBrowser(getValue());
      }
    };
  }

  @NotNull
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}