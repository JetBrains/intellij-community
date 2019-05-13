// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
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
  public void accept(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
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
