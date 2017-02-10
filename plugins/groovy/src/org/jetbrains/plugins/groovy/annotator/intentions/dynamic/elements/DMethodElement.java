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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.GrDynamicImplicitMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
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
  @NotNull
  public PsiMethod getPsi(PsiManager manager, final String containingClassName) {
    if (myImplicitMethod != null) return myImplicitMethod;

    Boolean aStatic = isStatic();
    myImplicitMethod = new GrDynamicImplicitMethod(manager, getName(), containingClassName, aStatic != null && aStatic.booleanValue(), myPairs, getType());
    return myImplicitMethod;
  }
}