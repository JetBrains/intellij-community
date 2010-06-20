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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public class GrLightIdentifier extends LightElement {

  private final String myText;

  public GrLightIdentifier(PsiManager manager, String text) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myText = text;
  }

  public IElementType getTokenType() {
    return GroovyTokenTypes.mIDENT;
  }

  public String getText() {
    return myText;
  }

  public PsiElement copy() {
    return new GrLightIdentifier(getManager(), myText);
  }

  public String toString() {
    return "PsiIdentifier:" + getText();
  }


}
