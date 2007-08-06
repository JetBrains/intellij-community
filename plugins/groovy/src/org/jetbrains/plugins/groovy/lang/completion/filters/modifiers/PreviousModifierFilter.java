/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author ilyas
 */
public class PreviousModifierFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    String[] modifiers = new String[]{"private", "public", "protected", "transient", "final", "abstract",
        "native", "threadsafe", "volatile", "strictfp", "synchronized"};
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement) element;

      for (String modifier : modifiers) {
        if (modifier.equals(psiElement.getText().trim())) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "Second filter for modifier keywords";
  }

}