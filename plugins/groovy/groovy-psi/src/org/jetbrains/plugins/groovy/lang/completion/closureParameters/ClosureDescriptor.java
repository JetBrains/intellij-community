/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.completion.closureParameters;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClosureDescriptor extends LightElement implements PsiElement {
  private final List<ClosureParameterInfo> myParams = new ArrayList<>();
  private Map myMethod;

  public ClosureDescriptor(PsiManager manager) {
    super(manager, GroovyLanguage.INSTANCE);
  }

  public List<ClosureParameterInfo> getParameters() {
    return Collections.unmodifiableList(myParams);
  }

  public void addParameter(@Nullable String type, String name) {
    myParams.add(new ClosureParameterInfo(type, name));
  }


  @Override
  public String toString() {
    return "closure descriptor";
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
    final GrClosureSignature closureSignature = GrClosureSignatureUtil.createSignature(signature);

    if (method instanceof ClsMethodImpl) method = ((ClsMethodImpl)method).getSourceMirrorMethod();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] typeArray = PsiType.createArray(parameters.length);
    ContainerUtil.map(parameters, parameter -> parameter.getType(), typeArray);
    return GrClosureSignatureUtil.isSignatureApplicable(closureSignature, typeArray, place);
  }

  private static PsiType convertToPsiType(String type, @NotNull PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }
}
