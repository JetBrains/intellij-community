// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias;

public class GrImportAliasImpl extends GroovyPsiElementImpl implements GrImportAlias {

  public GrImportAliasImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getNameElement() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  @Nullable
  @Override
  public String getName() {
    PsiElement nameElement = getNameElement();
    return nameElement == null ? null : nameElement.getText();
  }
}
