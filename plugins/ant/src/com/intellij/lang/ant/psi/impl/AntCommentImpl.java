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

import com.intellij.lang.ant.psi.AntComment;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

public class AntCommentImpl extends AntElementImpl implements AntComment {

  public AntCommentImpl(final AntStructuredElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public IElementType getTokenType() {
    return ((XmlComment) getSourceElement()).getTokenType();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntComment";
  }

  @NotNull
  public PsiReference[] getReferences() {
    return PsiReference.EMPTY_ARRAY;
  }
}
