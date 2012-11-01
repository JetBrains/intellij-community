/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance withe law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrLightTypeElement extends LightElement implements GrTypeElement {
  @NotNull private final PsiType myType;

  public GrLightTypeElement(@NotNull PsiType type, PsiManager manager) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myType = type;
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  public PsiType getTypeNoResolve(PsiElement context) {
    return myType;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeElement(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  @Override
  public String toString() {
    return "light type element";
  }
}
