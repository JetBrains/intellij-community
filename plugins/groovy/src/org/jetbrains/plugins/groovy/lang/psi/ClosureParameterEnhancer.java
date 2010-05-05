package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
public class ClosureParameterEnhancer extends AbstractClosureParameterEnhancer {

  @Override
  @Nullable
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    final PsiElement parent = closure.getParent();
    if (!(parent instanceof GrMethodCallExpression)) {
      return null;
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(closure.getProject()).getElementFactory();
    String methodName = findMethodName((GrMethodCallExpression)parent);
    //final GrExpression invokedExpression = methodCall.getInvokedExpression();
    //PsiType type = findQualifierType(methodCall);

    GrExpression expression = ((GrMethodCallExpression)parent).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return null;

    GrExpression qualifier = ((GrReferenceExpression)expression).getQualifierExpression();
    if (qualifier == null) return null;
    PsiType type = qualifier.getType();

    if (type == null) {
      return null;
    }

    if ("each".equals(methodName) ||
        "every".equals(methodName) ||
        "collect".equals(methodName) ||
        "find".equals(methodName) ||
        "findAll".equals(methodName) ||
        "findIndexOf".equals(methodName)) {
      PsiType res = findTypeForCollection(qualifier, factory, closure);
      if (closure.getParameters().length <= 1 && res != null) {
        return res;
      }

      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        if (closure.getParameters().length <= 1) {
          return getEntryForMap(type, factory, closure);
        }
        if (closure.getParameters().length == 2) {
          if (index == 0) {
            return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
          }
          return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
        }
      }
    }
    else if ("with".equals(methodName) && closure.getParameters().length <= 1) {
      return type;
    }
    else {
      final PsiParameter[] paramCount = closure.getAllParameters();
      if ("eachWithIndex".equals(methodName)) {
        PsiType res = findTypeForCollection(qualifier, factory, closure);
        if (closure.getParameters().length == 2 && res != null) {
          if (index == 0) {
            return res;
          }
          return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, closure);
        }
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          if (paramCount.length == 2) {
            if (index == 0) {
              return getEntryForMap(type, factory, closure);
            }
            return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, closure);
          }
          if (paramCount.length == 3) {
            if (index == 0) {
              return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
            }
            if (index == 1) {
              return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
            }
            return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, closure);
          }
        }
      }
      else if ("inject".equals(methodName) && paramCount.length == 2) {
        if (index == 0) {
          return factory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, closure);
        }

        PsiType res = findTypeForCollection(qualifier, factory, closure);
        if (res != null) {
          return res;
        }
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          return getEntryForMap(type, factory, closure);
        }
      }
    }
    return null;
  }


  @Nullable
  private static PsiType getEntryForMap(PsiType map, PsiElementFactory factory, PsiElement context) {
    PsiType key = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 0, true);
    PsiType value = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 1, true);
    if (key != null && key != PsiType.NULL && value != null && value != PsiType.NULL) {
      return factory.createTypeFromText("java.util.Map.Entry<" + key.getCanonicalText() + ", " + value.getCanonicalText() + ">", context);
    }
    return null;
  }

  @Nullable
  public static PsiType findTypeForCollection(GrExpression qualifier, PsiElementFactory factory, PsiElement context) {
    PsiType iterType = qualifier.getType();
    if (iterType == null) return null;
    if (iterType instanceof PsiArrayType) {
      return ((PsiArrayType)iterType).getComponentType();
    }
    if (iterType instanceof GrTupleType) {
      PsiType[] types = ((GrTupleType)iterType).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    if (factory.createTypeFromText("groovy.lang.IntRange", context).isAssignableFrom(iterType)) {
      return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
    }
    if (factory.createTypeFromText("groovy.lang.ObjectRange", context).isAssignableFrom(iterType)) {
      PsiElement element = qualifier;
      element = removeBrackets(element);
      if (element instanceof GrReferenceExpression) {
        GrReferenceExpression ref = (GrReferenceExpression)element;
        element = removeBrackets(ref.resolve());
      }
      if (element instanceof GrRangeExpression) {
        return getRangeElementType((GrRangeExpression)element);
      }
      return null;
    }

    PsiType res = PsiUtil.extractIterableTypeParameter(iterType, true);
    if (res != null) {
      return res;
    }

    if (iterType.equalsToText(CommonClassNames.JAVA_LANG_STRING) || iterType.equalsToText("java.io.File")) {
      return factory.createTypeFromText(CommonClassNames.JAVA_LANG_STRING, context);
    }
    return null;
  }

  private static PsiElement removeBrackets(PsiElement element) {
    while (element instanceof GrParenthesizedExpression) {
      element = ((GrParenthesizedExpression)element).getOperand();
    }
    return element;
  }

  @Nullable
  private static PsiType getRangeElementType(GrRangeExpression range) {
    GrExpression left = range.getLeftOperand();
    GrExpression right = range.getRightOperand();
    if (right != null) {
      final PsiType leftType = left.getType();
      final PsiType rightType = right.getType();
      if (leftType != null && rightType != null) {
        return TypesUtil.getLeastUpperBound(leftType, rightType, range.getManager());
      }
    }
    return null;
  }

  @Nullable
  private static String findMethodName(@NotNull GrMethodCallExpression methodCall) {
    GrExpression expression = methodCall.getInvokedExpression();
    if (expression instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)expression).getReferenceName();
    }
    return null;
  }
}
