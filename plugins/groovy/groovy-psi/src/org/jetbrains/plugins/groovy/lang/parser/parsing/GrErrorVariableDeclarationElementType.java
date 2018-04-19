// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.EmptyStubElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

public class GrErrorVariableDeclarationElementType extends EmptyStubElementType<GrVariableDeclaration> {

  public GrErrorVariableDeclarationElementType(String debugName) {
    super(debugName, GroovyLanguage.INSTANCE);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return false;
  }

  @Override
  public GrVariableDeclaration createPsi(@NotNull EmptyStub stub) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
