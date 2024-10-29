// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

/**
 * @author Dmitry.Krasilschikov
 */

public class GrMethodImpl extends GrMethodBaseImpl implements GrMethod {
  public GrMethodImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrMethodImpl(GrMethodStub stub) {
    super(stub, GroovyStubElementTypes.METHOD);
  }

  @Override
  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getPsi() instanceof GrTypeParameterList) {
      if (!getModifierList().hasExplicitVisibilityModifiers()) {
        getModifierList().setModifierProperty(GrModifier.DEF, true);
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public String toString() {
    return "Method";
  }

}
