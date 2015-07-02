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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicyEx;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author Max Medvedev
 */
public class GroovyClsCustomNavigationPolicy extends ClsCustomNavigationPolicyEx {
  @Override
  @Nullable
  public PsiElement getNavigationElement(@NotNull ClsMethodImpl clsMethod) {
    if (!isGroovyLanguage(clsMethod)) return null;

    PsiMethod source = clsMethod.getSourceMirrorMethod();
    if (source instanceof LightElement) {
      return source.getNavigationElement();
    }

    return null;
  }

  private static boolean isGroovyLanguage(ClsMethodImpl method) {
    return method.getContainingFile().getNavigationElement().getLanguage() == GroovyLanguage.INSTANCE;
  }
}
