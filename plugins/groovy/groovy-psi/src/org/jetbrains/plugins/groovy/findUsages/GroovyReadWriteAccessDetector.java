// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyReadWriteAccessDetector extends ReadWriteAccessDetector{
  @Override
  public boolean isReadWriteAccessible(@NotNull PsiElement element) {
    return element instanceof GrVariable;
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
    if (element instanceof GrVariable && ((GrVariable)element).getInitializerGroovy() != null) {
      return true;
    }
    return false;
  }

  @Override
  public @NotNull Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public @NotNull Access getExpressionAccess(@NotNull PsiElement expression) {
    if (expression instanceof GrExpression expr) {
      boolean readAccess = PsiUtil.isAccessedForReading(expr);
      boolean writeAccess = PsiUtil.isAccessedForWriting(expr);
      if (!writeAccess && expr instanceof GrReferenceExpression) {
        //when searching usages of fields, should show all found setters as a "only write usage"
        PsiElement actualReferee = ((GrReferenceExpression)expr).resolve();
        if (actualReferee instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)actualReferee)) {
          writeAccess = true;
          readAccess = false;
        }
      }
      if (writeAccess && readAccess) return Access.ReadWrite;
      return writeAccess ? Access.Write : Access.Read;
    }
    else if (expression instanceof PsiExpression expr) {
      boolean readAccess = com.intellij.psi.util.PsiUtil.isAccessedForReading(expr);
      boolean writeAccess = com.intellij.psi.util.PsiUtil.isAccessedForWriting(expr);
      if (!writeAccess && expr instanceof PsiReferenceExpression) {
        //when searching usages of fields, should show all found setters as a "only write usage"
        PsiElement actualReferee = ((PsiReferenceExpression)expr).resolve();
        if (actualReferee instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)actualReferee)) {
          writeAccess = true;
          readAccess = false;
        }
      }
      if (writeAccess && readAccess) return Access.ReadWrite;
      return writeAccess ? Access.Write : Access.Read;
    }
    else {
      return Access.Read;
    }
  }
}
