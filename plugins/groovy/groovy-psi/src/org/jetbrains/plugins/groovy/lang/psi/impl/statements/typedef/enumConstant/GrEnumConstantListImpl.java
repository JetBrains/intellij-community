/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
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
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumConstants(this);
  }

  public String toString() {
    return "Enumeration constants";
  }

  @Override
  public GrEnumConstant[] getEnumConstants() {
    return getStubOrPsiChildren(GroovyElementTypes.ENUM_CONSTANT, GrEnumConstant.ARRAY_FACTORY);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }
}
