// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrReferenceListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public class GrTypeParameterParameterExtendsListImpl extends GrReferenceListImpl {

  public GrTypeParameterParameterExtendsListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrTypeParameterParameterExtendsListImpl(@NotNull GrReferenceListStub stub,
                                                 @NotNull IStubElementType<GrReferenceListStub, GrReferenceList> nodeType) {
    super(stub, nodeType);
  }

  public String toString() {
    return "Type extends bounds list";
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public Role getRole() {
    return Role.EXTENDS_BOUNDS_LIST;
  }

  @Override
  protected IElementType getKeywordType() {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getKeyword() {
    return null;
  }
}
