// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

/**
 * @author ilyas
 */
public interface GrTryCatchStatement extends GroovyPsiElement, GrStatement {

  @Nullable
  GrTryResourceList getResourceList();

  @Nullable
  GrOpenBlock getTryBlock();

  @NotNull
  GrCatchClause[] getCatchClauses();

  @Nullable
  GrFinallyClause getFinallyClause();

  GrCatchClause addCatchClause(@NotNull GrCatchClause clause, @Nullable GrCatchClause anchorBefore);
}
