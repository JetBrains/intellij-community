package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  private Map<String, Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>>> map;

  private Map<String, Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>>> getMap() {
    if (map == null) {
      Map<String, Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>>> mapClasses = new HashMap<String, Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>>>();

      for (GrMethodReturnTypeDescriptor descriptor : GrMethodReturnTypeDescriptor.EP_NAME.getExtensions()) {
        assert StringUtil.isNotEmpty(descriptor.className);

        Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>> mapMethods = mapClasses.get(descriptor.className);
        if (mapMethods == null) {
          mapMethods = new HashMap<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>>();
          mapClasses.put(descriptor.className, mapMethods);
        }

        assert StringUtil.isNotEmpty(descriptor.methodName);
        List<Pair<GrMethodReturnTypeDescriptor.Param[], String>> pairs = mapMethods.get(descriptor.methodName);
        if (pairs == null) {
          pairs = new ArrayList<Pair<GrMethodReturnTypeDescriptor.Param[], String>>();
          mapMethods.put(descriptor.methodName, pairs);
        }

        GrMethodReturnTypeDescriptor.Param[] params;

        if (descriptor.anyParams != null) {
          assert descriptor.anyParams.length == 1;
          assert descriptor.params == null;
          assert pairs.isEmpty();

          params = null;
        }
        else {
          params = descriptor.params;
          if (params == null) params = GrMethodReturnTypeDescriptor.Param.EMPTY_ARRAY;

          for (GrMethodReturnTypeDescriptor.Param param : params) {
            assert StringUtil.isNotEmpty(param.type);
          }
        }

        assert StringUtil.isNotEmpty(descriptor.returnType);
        pairs.add(new Pair<GrMethodReturnTypeDescriptor.Param[], String>(params, descriptor.returnType));
      }

      map = mapClasses;
    }

    return map;
  }

  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression) {
    PsiMethod method = callExpression.resolveMethod();
    if (method == null) return null;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    Map<String, List<Pair<GrMethodReturnTypeDescriptor.Param[], String>>> methodMap = getMap().get(containingClass.getQualifiedName());
    if (methodMap == null) return null;

    List<Pair<GrMethodReturnTypeDescriptor.Param[], String>> pairs = methodMap.get(method.getName());
    if (pairs == null) return null;

    String typeName = null;

    mainLoop:
    for (Pair<GrMethodReturnTypeDescriptor.Param[], String> pair : pairs) {
      if (pair.first == null) {
        typeName = pair.second;
        break;
      }
      else {
        GrMethodReturnTypeDescriptor.Param[] params = pair.first;
        PsiParameterList parameterList = method.getParameterList();

        if (parameterList.getParametersCount() == params.length) {
          PsiParameter[] parameters = parameterList.getParameters();
          for (int i = 0; i < parameters.length; i++) {
            if (!TypesUtil.isClassType(parameters[i].getType(), params[i].type)) {
              continue mainLoop;
            }
          }

          typeName = pair.second;
          break;
        }
      }
    }

    if (typeName == null) return null;

    return JavaPsiFacade.getElementFactory(callExpression.getProject()).createTypeByFQClassName(typeName, callExpression.getResolveScope());
  }
}
