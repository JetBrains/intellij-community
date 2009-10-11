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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * element for wrapping xml out of the project tag, that can be xml prolog or smth
 */
public class AntOuterProjectElement extends AntElementImpl {
  private final int myStartOffset;
  private final String myText;

  public AntOuterProjectElement(final AntFile parent, final int startOffset, final String text) {
    super(parent, null);
    myStartOffset = startOffset;
    myText = text;
  }

  @NonNls
  public String toString() {
    return "XmlText: " + myText;
  }

  public boolean isPhysical() {
    return getParent().isPhysical();
  }

  public boolean isValid() {
    return getParent().isValid();
  }

  public PsiManager getManager() {
    return getParent().getManager();
  }

  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myStartOffset + myText.length());
  }

  public int getTextLength() {
    return myText.length();
  }

  public int getTextOffset() {
    return myStartOffset;
  }

  public String getText() {
    return myText;
  }

  @NotNull
  public char[] textToCharArray() {
    return myText.toCharArray();
  }

  public boolean textContains(char c) {
    return myText.indexOf(c, 0) >= 0;
  }
}
