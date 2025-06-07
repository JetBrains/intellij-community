// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

public abstract class GrStubElementBase<T extends StubElement> extends StubBasedPsiElementBase<T> implements GroovyPsiElement {

  protected GrStubElementBase(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrStubElementBase(final ASTNode node) {
    super(node);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    getParent().deleteChildRange(this, this);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    GroovyPsiElementImpl.acceptGroovyChildren(this, visitor);
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    final PsiFile containingFile = getContainingFile();
    final GlobalSearchScope elementScope = super.getResolveScope();
    return GrFileIndexUtil.isGroovySourceFile(containingFile)
           ? elementScope
           : GlobalSearchScope.fileScope(containingFile).union(elementScope);
  }
}
