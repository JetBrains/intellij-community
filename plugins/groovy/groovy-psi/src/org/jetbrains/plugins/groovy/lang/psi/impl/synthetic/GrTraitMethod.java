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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;

/**
 * Created by Max Medvedev on 25/04/14
 */
public class GrTraitMethod extends GrMethodWrapper {
  private final PsiSubstitutor mySubstitutor;

  protected GrTraitMethod(PsiMethod method, PsiSubstitutor substitutor) {
    super(method, substitutor);
    mySubstitutor = substitutor;
  }

  @Override
  public PsiType getReturnType() {
    return mySubstitutor.substitute(super.getReturnType());
  }

  public static GrTraitMethod create(PsiMethod method, PsiSubstitutor substitutor) {
    return new GrTraitMethod(method, substitutor);
  }
}
