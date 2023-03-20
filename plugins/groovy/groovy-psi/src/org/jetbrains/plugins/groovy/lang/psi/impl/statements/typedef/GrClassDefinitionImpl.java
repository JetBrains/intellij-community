// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrClassDefinitionImpl extends GrTypeDefinitionImpl implements GrClassDefinition {

  public GrClassDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrClassDefinitionImpl(final GrTypeDefinitionStub stub) {
    super(stub, GroovyStubElementTypes.CLASS_TYPE_DEFINITION);
  }

  @Override
  public String toString() {
    return "Class definition";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitClassDefinition(this);
  }
}
