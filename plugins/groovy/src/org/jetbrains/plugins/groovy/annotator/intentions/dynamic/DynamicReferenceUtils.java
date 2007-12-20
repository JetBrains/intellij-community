package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DynamicReferenceUtils {
  @Nullable
  public static PsiClass findDynamicValueContainingClass(GrReferenceExpression refExpr) {
    final PsiClass psiClass;
    if (refExpr.isQualified()) {
      GrExpression qualifier = refExpr.getQualifierExpression();
      PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) return null;

      psiClass = ((PsiClassType) type).resolve();
    } else {
      PsiElement refParent = refExpr.getParent();

      while (refParent != null && !(refParent instanceof GroovyFileBase)) {
        refParent = refParent.getParent();
      }

      if (refParent == null) return null;
      psiClass = ((GroovyFileBase) refParent).getScriptClass();
    }
    return psiClass;
  }


}
