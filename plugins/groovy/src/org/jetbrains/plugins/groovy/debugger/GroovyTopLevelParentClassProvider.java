/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.engine.TopLevelParentClassProvider;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Max Medvedev
 */
public class GroovyTopLevelParentClassProvider extends TopLevelParentClassProvider {
  @Nullable
  @Override
  protected PsiClass getCustomTopLevelParentClass(PsiClass psiClass) {
    if (!(psiClass instanceof GrTypeDefinition)) return null;

    PsiClass enclosing = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class, true);
    while (enclosing != null) {
      psiClass = enclosing;
      enclosing = PsiTreeUtil.getParentOfType(enclosing, PsiClass.class, true);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      final PsiFile file = psiClass.getContainingFile();
      if (file instanceof GroovyFile) {
        return ((GroovyFile)file).getScriptClass();
      }
    }

    return psiClass;
  }
}
