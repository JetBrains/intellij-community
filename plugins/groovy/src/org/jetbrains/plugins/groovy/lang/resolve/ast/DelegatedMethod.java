/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.OriginInfoAwareElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class DelegatedMethod extends LightMethod implements PsiMethod, PsiMirrorElement, OriginInfoAwareElement {
  private final PsiMethod myPrototype;

  public DelegatedMethod(@NotNull PsiMethod delegate, @NotNull PsiMethod prototype) {
    super(prototype.getManager(), delegate, delegate.getContainingClass(), delegate.getLanguage());
    myPrototype = prototype;
    setNavigationElement(myPrototype);
  }

  @NotNull
  @Override
  public PsiMethod getPrototype() {
    return myPrototype;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    PsiClass aClass = myPrototype.getContainingClass();
    if (aClass != null && aClass.getName() != null) {
      return "delegates to " + aClass.getName();
    }
    return null;
  }
}
