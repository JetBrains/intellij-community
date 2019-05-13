/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * Main classdef for Groovy element types, such as lexems or AST nodes
 *
 * @author ilyas
 */
public class GroovyElementType extends IElementType {

  private final String myDebugName;
  private final boolean myLeftBound;

  public GroovyElementType(String debugName) {
    this(debugName, false);
  }

  public GroovyElementType(String debugName, boolean boundToLeft) {
    super(debugName, GroovyLanguage.INSTANCE);
    myDebugName = debugName;
    myLeftBound = boundToLeft;
  }

  public String toString() {
    return myDebugName;
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
