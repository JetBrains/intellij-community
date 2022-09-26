// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;

public interface GrForStatement extends GrControlStatement, GrLoopStatement {

  @Nullable
  GrForClause getClause();

  @Override
  @Nullable
  GrStatement getBody();

  PsiElement getRParenth();
}
