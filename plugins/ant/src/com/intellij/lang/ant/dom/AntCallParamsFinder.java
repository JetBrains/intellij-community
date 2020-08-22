// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.dom;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */

public final class AntCallParamsFinder extends AntDomRecursiveVisitor {
  private final String myPropertyName;
  private final List<PsiElement> myResult = new ArrayList<>();

  private AntCallParamsFinder(@NotNull String propertyName) {
    myPropertyName = propertyName;
  }

  @Override
  public void visitAntDomElement(AntDomElement element) {
    if (!element.isDataType()) { // optimization
      super.visitAntDomElement(element);
    }
  }

  @Override
  public void visitAntDomAntCallParam(AntDomAntCallParam antCallParam) {
    if (myPropertyName.equals(antCallParam.getName().getStringValue())) {
      final PsiElement elem = antCallParam.getNavigationElement(myPropertyName);
      if (elem != null) {
        myResult.add(elem);
      }
    }
  }

  @NotNull
  public static List<PsiElement> resolve(@NotNull AntDomProject project, @NotNull String propertyName) {
    final AntCallParamsFinder resolver = new AntCallParamsFinder(propertyName);
    project.accept(resolver);
    return resolver.myResult;

  }
}
