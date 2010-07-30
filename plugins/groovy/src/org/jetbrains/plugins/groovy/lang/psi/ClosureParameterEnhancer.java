package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class ClosureParameterEnhancer extends AbstractClosureParameterEnhancer {
  private final Map<String, String> simpleTypes = new HashMap<String, String>();
  private final Set<String> iterations = new HashSet<String>();

  public ClosureParameterEnhancer() {
    simpleTypes.put("times", "java.lang.Integer");
    simpleTypes.put("upto", "java.lang.Integer");
    simpleTypes.put("downto", "java.lang.Integer");
    simpleTypes.put("step", "java.lang.Integer");
    simpleTypes.put("withObjectOutputStream", "java.io.ObjectOutputStream");//todo
    simpleTypes.put("withObjectInputStream", "java.io.ObjectInputStream");
    simpleTypes.put("withOutputStream", "java.io.OutputStream");
    simpleTypes.put("withInputStream", "java.io.InputStream");
    simpleTypes.put("withDataOutputStream", "java.io.DataOutputStream");
    simpleTypes.put("withDataInputStream", "java.io.DataInputStream");
    simpleTypes.put("eachLine", "java.lang.String");
    simpleTypes.put("eachFile", "java.io.File");
    simpleTypes.put("eachDir", "java.io.File");
    simpleTypes.put("eachFileRecurse", "java.io.File");
    simpleTypes.put("traverse", "java.io.File");
    simpleTypes.put("eachDirRecurse", "java.io.File");
    simpleTypes.put("eachFileMatch", "java.io.File");
    simpleTypes.put("eachDirMatch", "java.io.File");
    simpleTypes.put("withReader", "java.io.Reader");
    simpleTypes.put("withWriter", "java.io.Writer");
    simpleTypes.put("withWriterAppend", "java.io.Writer");
    simpleTypes.put("withPrintWriter", "java.io.PrintWriter");
    simpleTypes.put("eachByte", "byte");
    simpleTypes.put("transformChar", "String");
    simpleTypes.put("transformLine", "String");
    simpleTypes.put("filterLine", "String");
    simpleTypes.put("accept", "java.net.Socket");

    iterations.add("each");
    iterations.add("any");
    iterations.add("every");
    iterations.add("reverseEach");
    iterations.add("collect");
    iterations.add("collectAll");
    iterations.add("find");
    iterations.add("findAll");
    iterations.add("retainAll");
    iterations.add("removeAll");
    iterations.add("split");
    iterations.add("groupBy");
    iterations.add("groupEntriesBy");
    iterations.add("findLastIndexOf");
    iterations.add("findIndexValues");
    iterations.add("findIndexOf");

  }

  @Override
  @Nullable
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    final PsiElement parent = closure.getParent();
    if (!(parent instanceof GrMethodCallExpression)) {
      return null;
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(closure.getProject()).getElementFactory();
    String methodName = findMethodName((GrMethodCallExpression)parent);

    GrExpression expression = ((GrMethodCallExpression)parent).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return null;
    final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
    if (!(resolved instanceof GrGdkMethod)) return null;

    GrExpression qualifier = ((GrReferenceExpression)expression).getQualifierExpression();
    if (qualifier == null) return null;
    PsiType type = qualifier.getType();

    if (type == null) {
      return null;
    }

    final PsiParameter[] params = closure.getAllParameters();

    if (params.length == 1 && simpleTypes.containsKey(methodName)) {
      return factory.createTypeFromText(simpleTypes.get(methodName), closure);
    }

    if (iterations.contains(methodName)) {
      if (params.length == 1) {
        return findTypeForIteration(qualifier, factory, closure);
      }
      if (params.length == 2 && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        if (index == 0) {
          return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
        }
        return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
      }
    }
    else if ("with".equals(methodName) && params.length == 1) {
      return type;
    }
    else if ("eachWithIndex".equals(methodName)) {
      PsiType res = findTypeForIteration(qualifier, factory, closure);
      if (params.length == 2 && res != null) {
        if (index == 0) {
          return res;
        }
        return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, closure);
      }
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        if (params.length == 2) {
          if (index == 0) {
            return getEntryForMap(type, factory, closure);
          }
          return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, closure);
        }
        if (params.length == 3) {
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
    else if ("inject".equals(methodName) && params.length == 2) {
      if (index == 0) {
        return factory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, closure);
      }

      PsiType res = findTypeForIteration(qualifier, factory, closure);
      if (res != null) {
        return res;
      }
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return getEntryForMap(type, factory, closure);
      }
    }
    else if ("eachPermutation".equals(methodName) && params.length == 1) {
      final PsiType itemType = findTypeForIteration(qualifier, factory, closure);
      if (itemType != null) {
        return factory.createTypeFromText("java.util.ArrayList<" + itemType.getCanonicalText() + ">", closure);
      }
      return factory.createTypeFromText("java.util.ArrayList", closure);
    }
    else if ("withDefault".equals(methodName)) {
      if (params.length == 1 && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
      }
    }
    else if ("sort".equals(methodName)) {
      if (params.length < 3) {
        return findTypeForIteration(qualifier, factory, closure);
      }
    }
    else if ("withStream".equals(methodName)) {
      final PsiMethod method = ((GrMethodCallExpression)parent).resolveMethod();
      if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
          return parameters[0].getType();
        }
      }
    }
    else if ("withStreams".equals(methodName)) {
      if (index == 0) {
        return factory.createTypeFromText("java.io.InputStream", closure);
      }
      else if (index == 1) return factory.createTypeFromText("java.io.OutputStream", closure);
    }
    else if ("withObjectStreams".equals(methodName)) {
      if (index == 0) {
        return factory.createTypeFromText("java.io.ObjectInputStream", closure);
      }
      else if (index == 1) return factory.createTypeFromText("java.io.ObjectOutputStream", closure);
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
    return factory.createTypeFromText("java.util.Map.Entry", context);
  }

  @Nullable
  public static PsiType findTypeForIteration(GrExpression qualifier, PsiElementFactory factory, PsiElement context) {
    PsiType iterType = qualifier.getType();
    if (iterType == null) return null;
    if (iterType instanceof PsiArrayType) {
      return ((PsiArrayType)iterType).getComponentType();
    }
    if (iterType instanceof GrTupleType) {
      PsiType[] types = ((GrTupleType)iterType).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    if (InheritanceUtil.isInheritor(iterType, "groovy.lang.IntRange")) {
      return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
    }
    if (InheritanceUtil.isInheritor(iterType, "groovy.lang.ObjectRange")) {
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

    if (InheritanceUtil.isInheritor(iterType, CommonClassNames.JAVA_UTIL_MAP)) {
      return getEntryForMap(iterType, factory, context);
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
