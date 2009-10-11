/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;
import com.intellij.psi.PsiType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyVariableItemPresentation extends GroovyItemPresentation {
  private final GrVariable myVariable;

  public GroovyVariableItemPresentation(GrVariable variable) {
    super(variable);

    myVariable = variable;
  }

  public String getPresentableText() {
    StringBuffer presentableText = new StringBuffer();

    presentableText.append(myVariable.getName());
    GrTypeElement varTypeElement = myVariable.getTypeElementGroovy();

    if (varTypeElement != null) {
      PsiType varType = varTypeElement.getType();
      presentableText.append(":");
      presentableText.append(varType.getPresentableText());
    }
    return presentableText.toString();
  }
}
