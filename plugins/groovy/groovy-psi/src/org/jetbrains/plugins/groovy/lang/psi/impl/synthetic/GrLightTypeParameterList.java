// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

import java.util.ArrayList;
import java.util.List;

public class GrLightTypeParameterList extends LightElement implements GrTypeParameterList {

  private final List<GrTypeParameter> myTypeParameters = new ArrayList<>();

  public GrLightTypeParameterList(@NotNull PsiManager manager, @NotNull Language language) {
    super(manager, language);
  }

  public GrLightTypeParameterList(@NotNull PsiElement context) {
    this(context.getManager(), context.getLanguage());
  }

  @Override
  public GrTypeParameter @NotNull [] getTypeParameters() {
    return myTypeParameters.toArray(GrTypeParameter.EMPTY_ARRAY);
  }

  @Override
  public int getTypeParameterIndex(@NotNull PsiTypeParameter typeParameter) {
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
  public @NotNull GrLightTypeParameterList addTypeParameter(@NotNull GrTypeParameter typeParameter) {
    myTypeParameters.add(typeParameter);
    return this;
  }
}
