package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("GrDescriptorReturnTypeCalculator getClosureReturnType");

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

        if (descriptor.params != null) {
          params = descriptor.params;
          assert params != null;
          assert (descriptor.checkParamsType == null || descriptor.checkParamsType);

          for (GrMethodReturnTypeDescriptor.Param param : params) {
            assert StringUtil.isNotEmpty(param.type);
          }
        }
        else if (descriptor.checkParamsType != null && descriptor.checkParamsType) {
          params = GrMethodReturnTypeDescriptor.Param.EMPTY_ARRAY;
        }
        else {
          params = null;
        }

        assert StringUtil.isNotEmpty(descriptor.returnType);
        pairs.add(new Pair<GrMethodReturnTypeDescriptor.Param[], String>(params, descriptor.returnType));
      }

      map = mapClasses;
    }

    return map;
  }

  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod method) {
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

    if (typeName.equals("!closure")) {
      GrExpression[] allArguments = PsiUtil.getAllArguments(callExpression);
      GrClosableBlock closure = null;

      for (GrExpression argument : allArguments) {
        if (argument instanceof GrClosableBlock) {
          closure = (GrClosableBlock)argument;
          break;
        }
      }

      if (closure == null) return null;

      final GrClosableBlock finalClosure = closure;

      return ourGuard.doPreventingRecursion(callExpression, true, new Computable<PsiType>() {
        @Override
        public PsiType compute() {
          PsiType returnType = finalClosure.getReturnType();
          if (returnType == PsiType.VOID) return null;
          return returnType;
        }
      });
    }

    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createTypeFromText(typeName, callExpression);
  }
}
