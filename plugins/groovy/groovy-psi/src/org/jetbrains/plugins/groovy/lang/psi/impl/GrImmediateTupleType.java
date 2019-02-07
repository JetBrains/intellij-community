// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ven
 */
public class GrImmediateTupleType extends GrTupleType {

  private final List<PsiType> myComponentTypes;

  public GrImmediateTupleType(@NotNull List<PsiType> componentTypes, @NotNull JavaPsiFacade facade, @NotNull GlobalSearchScope scope) {
    super(scope, facade);
    myComponentTypes = componentTypes;
  }

  @Override
  public boolean isValid() {
    for (PsiType initializer : myComponentTypes) {
      if (initializer != null && !initializer.isValid()) return false;
    }
    return true;
  }

  @NotNull
  @Override
  protected List<PsiType> inferComponents() {
    return myComponentTypes;
  }

  @NotNull
  @Override
  public List<PsiType> getComponentTypes() {
    return myComponentTypes;
  }
}
