// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrEnumConstantListImpl extends GrStubElementBase<EmptyStub>
  implements GrEnumConstantList, StubBasedPsiElement<EmptyStub>, PsiListLikeElement {

  public GrEnumConstantListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantListImpl(EmptyStub stub) {
    super(stub, GroovyEmptyStubElementTypes.ENUM_CONSTANTS);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitEnumConstants(this);
  }

  @Override
  public String toString() {
    return "Enumeration constants";
  }

  @Override
  public GrEnumConstant[] getEnumConstants() {
    return getStubOrPsiChildren(GroovyStubElementTypes.ENUM_CONSTANT, GrEnumConstant.ARRAY_FACTORY);
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getEnumConstants());
  }
}
