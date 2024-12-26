// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrBindingVariable extends GrLightVariable implements GrVariable {
  private final GroovyFile myFile;

  public GrBindingVariable(final @NotNull GroovyFile file, @NotNull String name) {
    super(file.getManager(), name, CommonClassNames.JAVA_LANG_OBJECT, file);
    myFile = file;
  }

  @Override
  public PsiElement getContext() {
    return myFile;
  }

  @Override
  public @Nullable Icon getIcon(int flags) {
    return JetgroovyIcons.Groovy.Variable;
  }

  @Override
  public @Nullable GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  public @Nullable PsiType getTypeGroovy() {
    return null;
  }

  @Override
  public @Nullable PsiType getDeclaredType() {
    return null;
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    //todo?
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    return GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(getName());
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitVariable(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    //todo
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEquivalentTo(@Nullable PsiElement another) {
    return another instanceof GrBindingVariable &&
           StringUtil.equals(getName(), ((GrBindingVariable)another).getName()) &&
           getManager().areElementsEquivalent(getContainingFile(), another.getContainingFile());
  }

  @Override
  public String toString() {
    return "Binding variable";
  }
}
