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

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrLightTypeElement extends LightElement implements GrTypeElement {
  @NotNull private final PsiType myType;

  public GrLightTypeElement(@NotNull PsiType type, PsiManager manager) {
    super(manager, GroovyLanguage.INSTANCE);
    myType = type;
  }

  @NotNull
  @Override
  public PsiType getType() {
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
