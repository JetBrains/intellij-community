// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * Main classdef for Groovy element types, such as lexems or AST nodes
 *
 * @author ilyas
 */
public class GroovyElementType extends IElementType {
  private final boolean myLeftBound;

  public GroovyElementType(@NonNls String debugName) {
    this(debugName, false);
  }

  public GroovyElementType(String debugName, boolean boundToLeft) {
    super(debugName, GroovyLanguage.INSTANCE);
    myLeftBound = boundToLeft;
  }

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }

  public static abstract class PsiCreator extends GroovyElementType {
    protected PsiCreator(String debugName) {
      super(debugName);
    }

    @NotNull
    public abstract PsiElement createPsi(@NotNull ASTNode node);
  }
}
