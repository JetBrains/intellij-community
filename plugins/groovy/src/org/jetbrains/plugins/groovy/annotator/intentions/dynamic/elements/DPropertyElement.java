/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrDynamicImplicitProperty;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  private PsiVariable myPsi;

  //Do not use directly! Persistence component uses default constructor for deserializable
  public DPropertyElement() {
    super(null, null);
  }

  public DPropertyElement(String name, String type) {
    super(name, type);
  }

  public void clearCache() {
    myPsi = null;
  }

  @NotNull
  public PsiVariable getPsi(PsiManager manager, String containingClassName) {
    if (myPsi != null) return myPsi;
    myPsi = new GrDynamicImplicitProperty(manager, getName(), getType(), containingClassName);
    return myPsi;
  }
}
