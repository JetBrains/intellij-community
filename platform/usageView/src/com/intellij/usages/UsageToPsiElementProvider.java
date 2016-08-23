/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to provide appropriate psiElements from usages in Find Usages popup.
 * For instance, it's used in Find Usages popup to help ShowImplementationsAction show
 * psiElement containing a usage
 *
 * @author Konstantin Bulenkov
 */
public abstract class UsageToPsiElementProvider {
  public static final ExtensionPointName<UsageToPsiElementProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.usageToPsiElementProvider");

  @Nullable
  public abstract PsiElement getAppropriateParentFrom(PsiElement element);

  @Nullable
  public static PsiElement findAppropriateParentFrom(@NotNull PsiElement element) {
    for (UsageToPsiElementProvider provider : EP_NAME.getExtensions()) {
      final PsiElement parent = provider.getAppropriateParentFrom(element);
      if (parent != null) {
        return parent;
      }
    }
    return null;
  }
}
