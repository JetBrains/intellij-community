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

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyVariableItemPresentation extends GroovyItemPresentation {
  private final PsiVariable myVariable;

  public GroovyVariableItemPresentation(PsiVariable variable) {
    super(variable);

    myVariable = variable;
  }

  public String getPresentableText() {
    StringBuilder presentableText = new StringBuilder();

    presentableText.append(myVariable.getName());
    if (!(myVariable instanceof GrVariable) || ((GrVariable)myVariable).getTypeElementGroovy() != null) {
      PsiType varType = myVariable.getType();
      presentableText.append(":");
      presentableText.append(varType.getPresentableText());
    }
    return presentableText.toString();
  }
}
