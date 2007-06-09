package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class MethodTypeInferencer {
  @Nullable
  public static PsiType inferMethodReturnType (GrMethod method) {
    GrOpenBlock body = method.getBlock();
    if (body == null) return null;
    method.putCopyableUserData(ResolveUtil.IS_BEING_RESOLVED, true);

    List<GrReturnStatement> returns = new ArrayList<GrReturnStatement>();
    collectReturns(body, returns);

    PsiType result = null;
    PsiManager manager = method.getManager();
    for (GrReturnStatement returnStatement : returns) {
      GrExpression value = returnStatement.getReturnValue();
      if (value != null) {
        result = upperBound(result, value.getType(), manager);
      }
    }

    boolean isVoid = returns.size() == 0;

    GrStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      GrStatement last = statements[statements.length - 1];
      if (last instanceof GrExpression) {
        result = upperBound(((GrExpression) last).getType(), result, manager);
        isVoid = false;
      }
    }

    method.putCopyableUserData(ResolveUtil.IS_BEING_RESOLVED, null);
    if (isVoid) return PsiType.VOID;
    
    return result;
  }

  private static PsiType upperBound(PsiType type1, PsiType type2, PsiManager manager) {
    if (type1 == null) return type2;
    if (type2 == null) return type1;
    if (type1.isAssignableFrom(type2)) return type1;
    if (type2.isAssignableFrom(type1)) return type2;
    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }

  private static void collectReturns(PsiElement element, List<GrReturnStatement> returns) {
    if (element instanceof GrReturnStatement) {
      returns.add((GrReturnStatement) element);
    } else {
      PsiElement child = element.getFirstChild();
      while(child != null) {
        collectReturns(child, returns);
        child = child.getNextSibling();
      }
    }
  }
}
