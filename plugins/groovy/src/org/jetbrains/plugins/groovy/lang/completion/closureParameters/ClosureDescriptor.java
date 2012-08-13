/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ClosureDescriptor extends LightElement implements PsiElement {
  private final List<ClosureParameterInfo> myParams = new ArrayList<ClosureParameterInfo>();
  private MethodSignature myMethodSignature;
  private GrClosureSignature myClosureSignature;


  public ClosureDescriptor(PsiManager manager) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
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

  public void setMethodSignature(MethodSignature methodSignature) {
    myMethodSignature = methodSignature;
    myClosureSignature = GrClosureSignatureUtil.createSignature(methodSignature);
  }

  public boolean isMethodApplicable(PsiMethod method, GroovyPsiElement place) {
    assert myMethodSignature != null;
    assert myClosureSignature != null;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] types = new PsiType[parameters.length];
    ContainerUtil.map(parameters, new Function<PsiParameter, PsiType>() {
      @Override
      public PsiType fun(PsiParameter parameter) {
        return TypeConversionUtil.erasure(parameter.getType());
      }
    }, types);
    return method.getName().equals(myMethodSignature.getName()) &&
           GrClosureSignatureUtil.isSignatureApplicable(myClosureSignature, types, place);
  }
}
