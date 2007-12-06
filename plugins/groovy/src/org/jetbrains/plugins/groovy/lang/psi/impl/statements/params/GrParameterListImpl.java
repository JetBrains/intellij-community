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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterListImpl extends GroovyPsiElementImpl implements GrParameterList {
  public GrParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  public String toString() {
    return "Parameter list";
  }

  @NotNull
  public GrParameter[] getParameters() {
    return findChildrenByClass(GrParameter.class);
  }

  public int getParameterIndex(PsiParameter parameter) {
    PsiParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].equals(parameter)) return i;
    }

    return -1;
  }

  public int getParametersCount() {
    return getParameters().length;
  }

  public void addParameter(GrParameter parameter) {
    GrParameter[] params = getParameters();
    if (params.length == 0) {
      getNode().addChild(parameter.getNode());
    } else {
      GrParameter last = params[params.length - 1];
      getNode().addChild(parameter.getNode(), last.getNode());
      getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", last.getNode());
    }
  }
}
