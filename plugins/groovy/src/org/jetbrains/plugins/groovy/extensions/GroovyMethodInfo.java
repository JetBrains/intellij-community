package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodInfo {
  
  private volatile static Map<String, Map<String, List<GroovyMethodInfo>>> MAP;
  
  private List<String> myParams;
  
  private String myReturnType;

  private Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> myNamedArguments;

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
    return methodInfos == null ? Collections.<GroovyMethodInfo>emptyList() : methodInfos;
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

    GroovyMethodInfo info = null;
    
    List<String> params = method.getParams();
    
    for (GroovyMethodInfo methodInfo : methodsList) {
      if (params == null ? methodInfo.myParams == null : params.equals(methodInfo.myParams)) {
        info = methodInfo;
        break;
      }
    }
    
    if (info == null) {
      info = new GroovyMethodInfo();
      info.myParams = params;
      methodsList.add(info);
    }

    if (method.returnType != null) {
      assert info.myReturnType == null;
      info.myReturnType = method.returnType;
    }

    Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> namedArgumentsMap = method.getArgumentsMap();
    if (namedArgumentsMap != null) {
      assert info.myNamedArguments == null;
      info.myNamedArguments = namedArgumentsMap;
    }
  }

  @Nullable
  public String getReturnType() {
    return myReturnType;
  }

  @Nullable
  public Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> getNamedArguments() {
    return myNamedArguments;
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
