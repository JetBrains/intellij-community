// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.GrDynamicImplicitMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;

import java.util.ArrayList;
import java.util.List;

public class DMethodElement extends DItemElement {
  public List<ParamInfo> myPairs = new ArrayList<>();
  private GrDynamicImplicitMethod myImplicitMethod;

  @SuppressWarnings("UnusedDeclaration") //for serialization
  public DMethodElement() {
    super(null, null, null);
  }

  public DMethodElement(Boolean isStatic, String name, String returnType, List<ParamInfo> pairs) {
    super(isStatic, name, returnType);

    myPairs = pairs;
  }

  public List<ParamInfo> getPairs() {
    return myPairs;
  }

  @Override
  public void clearCache() {
    myImplicitMethod = null;
  }

  @Override
  public @NotNull PsiMethod getPsi(PsiManager manager, final String containingClassName) {
    if (myImplicitMethod != null) return myImplicitMethod;

    Boolean aStatic = isStatic();
    myImplicitMethod = new GrDynamicImplicitMethod(manager, getName(), containingClassName, aStatic != null && aStatic.booleanValue(), myPairs, getType());
    return myImplicitMethod;
  }
}