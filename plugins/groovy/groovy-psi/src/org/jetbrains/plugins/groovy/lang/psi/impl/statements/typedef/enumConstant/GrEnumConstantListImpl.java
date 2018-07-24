// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantListImpl extends GrStubElementBase<EmptyStub> implements GrEnumConstantList, StubBasedPsiElement<EmptyStub> {

  public GrEnumConstantListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantListImpl(EmptyStub stub) {
    super(stub, GroovyElementTypes.ENUM_CONSTANTS);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitEnumConstants(this);
  }

  public String toString() {
    return "Enumeration constants";
  }

  @Override
  public GrEnumConstant[] getEnumConstants() {
    return getStubOrPsiChildren(GroovyElementTypes.ENUM_CONSTANT, GrEnumConstant.ARRAY_FACTORY);
  }
}
