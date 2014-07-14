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
package org.jetbrains.plugins.groovy.byteCodeViewer;

import com.intellij.byteCodeViewer.ClassSearcher;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * Created by Max Medvedev on 8/23/13
 */
public class GroovyScriptClassSearcher implements ClassSearcher {
  @Nullable
  @Override
  public PsiClass findClass(@NotNull PsiElement place) {
    if (place.getLanguage() == GroovyLanguage.INSTANCE) {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
      while (containingClass instanceof PsiTypeParameter) {
        containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
      }
      if (containingClass != null) return containingClass;

      PsiFile file = place.getContainingFile();
      if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
        return ((GroovyFile)file).getScriptClass();
      }
    }
    return null;
  }
}
