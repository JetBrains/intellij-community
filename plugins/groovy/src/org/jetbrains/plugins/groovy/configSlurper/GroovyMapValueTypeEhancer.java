package org.jetbrains.plugins.groovy.configSlurper;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyMapContentProvider;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMapValueTypeEhancer extends GrReferenceTypeEnhancer {
  @Override
  public PsiType getReferenceType(GrReferenceExpression ref, @Nullable PsiElement resolved) {
    if (resolved != null) return null;

    GrExpression qualifierExpression = ref.getQualifierExpression();
    if (qualifierExpression == null || !GroovyPsiManager.isInheritorCached(qualifierExpression.getType(), CommonClassNames.JAVA_UTIL_MAP)) {
      return null;
    }

    PsiElement qResolved;

    if (qualifierExpression instanceof GrReferenceExpression) {
      qResolved = ((GrReferenceExpression)qualifierExpression).resolve();
    }
    else if (qualifierExpression instanceof GrMethodCall) {
      qResolved = ((GrMethodCall)qualifierExpression).resolveMethod();
    }
    else {
      return null;
    }

    String key = ref.getName();
    if (key == null) return null;

    for (GroovyMapContentProvider provider : GroovyMapContentProvider.EP_NAME.getExtensions()) {
      PsiType type = provider.getValueType(qualifierExpression, qResolved, key);
      if (type != null) {
        return type;
      }
    }

    return null;
  }
}
