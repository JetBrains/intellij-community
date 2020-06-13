// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

public final class GroovyRunnerPsiUtil {
  @Nullable
  public static PsiClass getRunningClass(@Nullable PsiElement element) {
    if (element == null) return null;
    if (DumbService.isDumb(element.getProject())) return null;

    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;

    for (PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
         clazz != null;
         clazz = PsiTreeUtil.getParentOfType(clazz, PsiClass.class)) {
      if (canBeRunByGroovy(clazz)) return clazz;
    }

    if (((GroovyFile)file).isScript()) return ((GroovyFile)file).getScriptClass();

    final PsiClass[] classes = ((GroovyFile)file).getClasses();
    if (classes.length > 0) {
      return classes[0];
    }

    return null;
  }

  public static boolean isRunnable(@Nullable final PsiClass psiClass) {
    if (psiClass == null) return false;
    final PsiClass runnable =
      JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_LANG_RUNNABLE, psiClass.getResolveScope());
    if (runnable == null) return false;

    return psiClass instanceof GrTypeDefinition &&
           !(psiClass instanceof PsiAnonymousClass) &&
           !psiClass.isInterface() &&
           psiClass.isInheritor(runnable, true);
  }

  public static boolean canBeRunByGroovy(final PsiClass psiClass) {
    return psiClass instanceof GroovyScriptClass ||
           isRunnable(psiClass) ||
           psiClass instanceof GrTypeDefinition && PsiMethodUtil.hasMainMethod(psiClass);
  }
}
