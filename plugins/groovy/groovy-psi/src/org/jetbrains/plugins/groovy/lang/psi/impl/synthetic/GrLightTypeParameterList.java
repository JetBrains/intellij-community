/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

import java.util.List;

public class GrLightTypeParameterList extends LightElement implements GrTypeParameterList {

  private final List<GrTypeParameter> myTypeParameters = ContainerUtil.newArrayList();

  public GrLightTypeParameterList(@NotNull PsiManager manager, @NotNull Language language) {
    super(manager, language);
  }

  public GrLightTypeParameterList(@NotNull PsiElement context) {
    this(context.getManager(), context.getLanguage());
  }

  @Override
  public GrTypeParameter[] getTypeParameters() {
    return myTypeParameters.toArray(GrTypeParameter.EMPTY_ARRAY);
  }

  @Override
  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    //noinspection SuspiciousMethodCalls
    return myTypeParameters.indexOf(typeParameter);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  @Override
  public String toString() {
    return "Light Groovy type parameter list";
  }

  @SuppressWarnings("unused")
  @NotNull
  public GrLightTypeParameterList addTypeParameter(@NotNull GrTypeParameter typeParameter) {
    myTypeParameters.add(typeParameter);
    return this;
  }
}
