/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Main classdef for Groovy element types, such as lexems or AST nodes
 *
 * @author ilyas
 */
public class GroovyElementType extends IElementType {

  private String debugName = null;

  public GroovyElementType(String debugName) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
    this.debugName = debugName;
  }

  public String toString() {
    return debugName;
  }

  public static abstract class PsiCreator extends GroovyElementType {
    protected PsiCreator(String debugName) {
      super(debugName);
    }

    public abstract GroovyPsiElement createPsi(@NotNull ASTNode node);
  }
}
