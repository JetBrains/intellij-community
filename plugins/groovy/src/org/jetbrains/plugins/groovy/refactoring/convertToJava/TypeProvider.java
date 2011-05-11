/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Medvedev Max
 */
public class TypeProvider {
  public TypeProvider() {
  }

  @NotNull
  public PsiType getReturnType(PsiMethod method) {
    if (method instanceof GrMethod) {
      GrTypeElement typeElement = ((GrMethod)method).getReturnTypeElementGroovy();
      if (typeElement != null) return typeElement.getType();
    }
    final PsiType smartReturnType = PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null) return smartReturnType;

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
  }

  @NotNull
  public  PsiType getVarType(GrVariable variable) {
    PsiType type = variable.getDeclaredType();
    if (type == null) {
      type = variable.getTypeGroovy();
    }
    if (type == null) {
      type = variable.getType();
    }
    return type;
  }

  @NotNull
  public PsiType getParameterType(PsiParameter parameter) {
    return parameter.getType(); //todo make smarter
  }


}
