// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion.closureParameters;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClosureDescriptor {

  private final List<ClosureParameterInfo> myParams = new ArrayList<>();
  private Map myMethod;

  public List<ClosureParameterInfo> getParameters() {
    return Collections.unmodifiableList(myParams);
  }

  public void addParameter(@Nullable String type, String name) {
    myParams.add(new ClosureParameterInfo(type, name));
  }

  public void setMethod(Map method) {
    this.myMethod = method;
  }

  public boolean isMethodApplicable(PsiMethod method, GroovyPsiElement place) {
    String name = String.valueOf(myMethod.get("name"));
    if (name == null || !name.equals(method.getName())) return false;

    List<PsiType> types = new ArrayList<>();
    final Object params = myMethod.get("params");
    if (params instanceof Map) {
      boolean first = true;
      for (Object paramName : ((Map)params).keySet()) {
        Object value = ((Map)params).get(paramName);
        boolean isNamed = first && value instanceof List;
        first = false;
        String typeName = isNamed ? CommonClassNames.JAVA_UTIL_MAP : String.valueOf(value);
        types.add(convertToPsiType(typeName, place));
      }
    }
    else if (params instanceof List) {
      for (Object param : ((List)params)) {
        PsiTypeParameterList typeParameterList = method.getTypeParameterList();
        types.add(convertToPsiType(String.valueOf(param), typeParameterList != null ? typeParameterList : method));
      }
    }
    final boolean isConstructor = Boolean.TRUE.equals(myMethod.get("constructor"));
    final MethodSignature signature = MethodSignatureUtil
      .createMethodSignature(name, types.toArray(PsiType.createArray(types.size())), method.getTypeParameters(), PsiSubstitutor.EMPTY, isConstructor);
    final GrSignature closureSignature = GrClosureSignatureUtil.createSignature(signature);

    if (method instanceof ClsMethodImpl) method = ((ClsMethodImpl)method).getSourceMirrorMethod();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] typeArray = 
    ContainerUtil.map(parameters, parameter -> parameter.getType(), PsiType.createArray(parameters.length));
    return GrClosureSignatureUtil.isSignatureApplicable(Collections.singletonList(closureSignature), typeArray, place);
  }

  private static PsiType convertToPsiType(String type, @NotNull PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }
}
