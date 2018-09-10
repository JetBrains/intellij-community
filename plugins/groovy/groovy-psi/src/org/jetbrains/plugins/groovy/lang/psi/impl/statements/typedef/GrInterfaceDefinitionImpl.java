// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author Dmitry.Krasilschikov
 * @date 16.03.2007
 */
public class GrInterfaceDefinitionImpl extends GrTypeDefinitionImpl implements GrInterfaceDefinition {

  public GrInterfaceDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.INTERFACE_DEFINITION);
  }

  public GrInterfaceDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Interface definition";
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitInterfaceDefinition(this);
  }
}
