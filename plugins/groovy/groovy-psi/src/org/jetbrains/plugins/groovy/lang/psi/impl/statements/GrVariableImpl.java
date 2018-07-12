// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  public void accept(@NotNull GroovyElementVisitor visitor) {
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
