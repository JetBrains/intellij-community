package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.*;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodInfo {
  
  private volatile static Map<String, Map<String, List<GroovyMethodInfo>>> MAP;
  
  private final List<String> myParams;
  
  private final String myReturnType;
  private final String myReturnTypeCalculatorClassName;
  private PairFunction<GrMethodCall, PsiMethod, PsiType> myReturnTypeCalculatorInstance;

  private final Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> myNamedArguments;
  private final  String myNamedArgProviderClassName;
  private GroovyMethodDescriptor.NamedArgumentProvider myNamedArgProviderInstance;

  public static Map<String, Map<String, List<GroovyMethodInfo>>> getMethodMap() {
    Map<String, Map<String, List<GroovyMethodInfo>>> res = MAP;
    
    if (res == null) {
      res = new HashMap<String, Map<String, List<GroovyMethodInfo>>>();

      for (GroovyClassDescriptor classDescriptor : GroovyClassDescriptor.EP_NAME.getExtensions()) {
        for (GroovyMethodDescriptor method : classDescriptor.methods) {
          addMethodDescriptor(res, method, classDescriptor.className);
        }
      }

      for (GroovyMethodDescriptorExtension methodDescriptor : GroovyMethodDescriptorExtension.EP_NAME.getExtensions()) {
        addMethodDescriptor(res, methodDescriptor, methodDescriptor.className);
      }

      for (Map<String, List<GroovyMethodInfo>> methodMap : res.values()) {
        List<GroovyMethodInfo> unnamedMethodDescriptors = methodMap.get(null);
        if (unnamedMethodDescriptors != null) {
          for (Map.Entry<String, List<GroovyMethodInfo>> entry : methodMap.entrySet()) {
            if (entry.getKey() != null) {
              entry.getValue().addAll(unnamedMethodDescriptors);
            }
          }
        }
      }
      
      MAP = res;
    }

    return res;
  }

  public static List<GroovyMethodInfo> getInfos(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return Collections.emptyList();

    Map<String, Map<String, List<GroovyMethodInfo>>> map = getMethodMap();

    Map<String, List<GroovyMethodInfo>> methodMap = map.get(containingClass.getQualifiedName());
    if (methodMap == null) return Collections.emptyList();

    List<GroovyMethodInfo> methodInfos = methodMap.get(method.getName());
    if (methodInfos == null) {
      methodInfos = methodMap.get(null);
    }

    return methodInfos == null ? Collections.<GroovyMethodInfo>emptyList() : methodInfos;
  }

  public GroovyMethodInfo(GroovyMethodDescriptor method) {
    myParams = method.getParams();
    myReturnType = method.returnType;
    myReturnTypeCalculatorClassName = method.returnTypeCalculator;
    assert myReturnType == null || myReturnTypeCalculatorClassName == null;

    myNamedArguments = method.getArgumentsMap();
    myNamedArgProviderClassName = method.namedArgsProvider;
    assert myNamedArguments == null || myNamedArgProviderClassName == null;
  }

  private static void addMethodDescriptor(Map<String, Map<String, List<GroovyMethodInfo>>> res,
                                          GroovyMethodDescriptor method,
                                          @NotNull String className) {
    Map<String, List<GroovyMethodInfo>> methodMap = res.get(className);
    if (methodMap == null) {
      methodMap = new HashMap<String, List<GroovyMethodInfo>>();
      res.put(className, methodMap);
    }

    List<GroovyMethodInfo> methodsList = methodMap.get(method.methodName);
    if (methodsList == null) {
      methodsList = new ArrayList<GroovyMethodInfo>();
      methodMap.put(method.methodName, methodsList);
    }

    methodsList.add(new GroovyMethodInfo(method));
  }

  @Nullable
  public String getReturnType() {
    return myReturnType;
  }

  public String getReturnTypeCalculatorClassName() {
    return myReturnTypeCalculatorClassName;
  }

  public PairFunction<GrMethodCall, PsiMethod, PsiType> getReturnTypeCalculator() {
    if (myReturnTypeCalculatorClassName == null) return null;
    
    if (myReturnTypeCalculatorInstance == null) {
      try {
        myReturnTypeCalculatorInstance = (PairFunction<GrMethodCall, PsiMethod, PsiType>)Class.forName(myReturnTypeCalculatorClassName).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    return myReturnTypeCalculatorInstance;
  }

  public void addNamedArguments(Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> res, @NotNull GrCall call, @NotNull PsiMethod method) {
    if (myNamedArguments != null) {
      res.putAll(myNamedArguments);
    }
    else if (myNamedArgProviderClassName != null) {
      if (myNamedArgProviderInstance == null) {
        try {
          myNamedArgProviderInstance = (GroovyMethodDescriptor.NamedArgumentProvider)Class.forName(myNamedArgProviderClassName).newInstance();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      myNamedArgProviderInstance.collectNamedArguments(res, call, method);
    }
  }

  public boolean isProvideNamedArguments() {
    return myNamedArguments != null || myNamedArgProviderClassName != null;
  }

  public boolean isApplicable(@NotNull PsiMethod method) {
    if (myParams == null) {
      return true;
    }

    PsiParameterList parameterList = method.getParameterList();

    if (parameterList.getParametersCount() != myParams.size()) return false;

    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (!TypesUtil.isClassType(parameters[i].getType(), myParams.get(i))) {
        return false;
      }
    }

    return true;
  }
}
