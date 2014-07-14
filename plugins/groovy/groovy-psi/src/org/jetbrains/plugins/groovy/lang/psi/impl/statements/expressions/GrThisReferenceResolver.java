package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 15/06/14
 */
public class GrThisReferenceResolver {
  @Nullable("null if ref is not actually 'this' reference")
  public static GroovyResolveResult[] resolveThisExpression(@NotNull GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier == null) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof GrConstructorInvocation) {
        return ((GrConstructorInvocation)parent).multiResolve(false);
      }
      else {
        PsiClass aClass = PsiUtil.getContextClass(ref);
        if (aClass != null) {
          return new GroovyResolveResultImpl[]{new GroovyResolveResultImpl(aClass, null, null, PsiSubstitutor.EMPTY, true, true)};
        }
      }
    }
    else if (qualifier instanceof GrReferenceExpression) {
      GroovyResolveResult result = ((GrReferenceExpression)qualifier).advancedResolve();
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass && PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, ref, false)) {
        return new GroovyResolveResult[]{result};
      }
    }

    return null;
  }
}
