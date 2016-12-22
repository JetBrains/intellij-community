/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

/**
 * @author Dmitry.Krasilschikov
 * @date 11.04.2007
 */
public class GrVariableImpl extends GrVariableBaseImpl<GrVariableStub> implements GrVariable {

  public GrVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableImpl(@NotNull GrVariableStub stub) {
    super(stub, GroovyElementTypes.VARIABLE);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariable(this);
  }

  public String toString() {
    return "Variable";
  }

  @Override
  public PsiElement getContext() {
    return ResolveUtil.isScriptField(this) ? getContainingFile() : super.getContext();
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    GrScriptField field = ResolveUtil.findScriptField(this);
    return field != null ? field.getUseScope() : super.getUseScope();
  }

  @Nullable
  @Override
  protected Icon getElementIcon(int flags) {
    return JetgroovyIcons.Groovy.Variable;
  }
}
