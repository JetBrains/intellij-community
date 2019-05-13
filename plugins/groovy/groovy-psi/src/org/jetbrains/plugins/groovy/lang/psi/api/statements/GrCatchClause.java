// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public interface GrCatchClause extends GroovyPsiElement {

  @Nullable
  GrParameter getParameter();

  @Nullable
  GrOpenBlock getBody();

  @Nullable
  PsiElement getLBrace();

  @Nullable
  PsiElement getRParenth();
}
