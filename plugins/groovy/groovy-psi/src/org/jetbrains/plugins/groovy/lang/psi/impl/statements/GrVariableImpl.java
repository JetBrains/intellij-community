// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

import static org.jetbrains.plugins.groovy.lang.typing.TuplesKt.getMultiAssignmentType;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrVariableImpl extends GrVariableBaseImpl<GrVariableStub> implements GrVariable {

  public GrVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableImpl(@NotNull GrVariableStub stub) {
    super(stub, GroovyStubElementTypes.VARIABLE);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitVariable(this);
  }

  @Override
  public String toString() {
    return "Variable";
  }

  @Override
  public PsiElement getContext() {
    return ResolveUtil.isScriptField(this) ? getContainingFile() : super.getContext();
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    GrScriptField field = ResolveUtil.findScriptField(this);
    return field != null ? field.getUseScope() : super.getUseScope();
  }

  @Override
  protected @Nullable Icon getElementIcon(int flags) {
    return JetgroovyIcons.Groovy.Variable;
  }

  @Override
  public @Nullable GrExpression getInitializerGroovy() {
    final PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration declaration) {
      if (declaration.isTuple()) {
        GrExpression rValue = declaration.getTupleInitializer();
        if (!(rValue instanceof GrListOrMap)) {
          return null;
        }
        int position = ArrayUtil.indexOf(declaration.getVariables(), this);
        if (position < 0) {
          return null;
        }
        final GrExpression[] initializers = ((GrListOrMap)rValue).getInitializers();
        if (position < initializers.length) {
          return initializers[position];
        }
        return null;
      }
    }
    return super.getInitializerGroovy();
  }

  @Override
  public @Nullable PsiType getInitializerType() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration declaration) {
      if (declaration.isTuple()) {
        GrExpression rValue = declaration.getTupleInitializer();
        if (rValue == null) {
          return null;
        }
        int position = ArrayUtil.indexOf(declaration.getVariables(), this);
        if (position < 0) {
          return null;
        }
        return getMultiAssignmentType(rValue, position);
      }
    }
    return super.getInitializerType();
  }
}
