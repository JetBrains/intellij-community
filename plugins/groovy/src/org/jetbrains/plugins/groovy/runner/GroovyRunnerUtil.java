/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

/**
 * @author Max Medvedev
 */
public class GroovyRunnerUtil {
  @Nullable
  public static PsiClass getRunningClass(PsiElement element) {
    final PsiFile file = element.getContainingFile();

    if (!(file instanceof GroovyFile)) return null;
    if (((GroovyFile)file).isScript()) return ((GroovyFile)file).getScriptClass();

    final PsiClass[] classes = ((GroovyFile)file).getClasses();
    if (classes.length > 0) {
      return classes[0];
    }

    return null;
  }

  public static boolean isRunnable(final PsiClass psiClass) {
    if (!(psiClass instanceof GrTypeDefinition) && !(psiClass instanceof GroovyScriptClass)) return false;
    if (psiClass instanceof PsiAnonymousClass) return false;
    if (psiClass.isInterface()) return false;
    final PsiClass runnable = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_LANG_RUNNABLE, psiClass.getResolveScope());
    if (runnable == null) return false;
    return psiClass.isInheritor(runnable, true);
  }

  public static boolean canBeRunByGroovy(final PsiClass psiClass) {
    if (isRunnable(psiClass)) {
      return true;
    }

    if (PsiMethodUtil.hasMainMethod(psiClass) && (psiClass instanceof GrTypeDefinition || psiClass instanceof GroovyScriptClass)) {
      return true;
    }

    return false;
  }
}
