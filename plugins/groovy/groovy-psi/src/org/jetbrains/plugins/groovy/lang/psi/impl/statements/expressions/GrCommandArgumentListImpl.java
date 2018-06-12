// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl;

/**
 * @author ilyas
 */
public class GrCommandArgumentListImpl extends GrArgumentListImpl implements GrCommandArgumentList {

  public GrCommandArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Command arguments";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitCommandArguments(this);
  }
}
