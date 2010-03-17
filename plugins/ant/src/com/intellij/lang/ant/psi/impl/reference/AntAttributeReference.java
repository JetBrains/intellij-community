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
package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Ant attribute reference serves only for completion.
 */
public class AntAttributeReference extends AntGenericReference {

  public AntAttributeReference(final AntStructuredElement element,
                               final String str,
                               final TextRange textRange,
                               final XmlAttribute attribute) {
    super(element, str, textRange, attribute);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public PsiElement resolve() {
    return null;
  }

  @NotNull
  public Object[] getVariants() {
    final AntTypeDefinition def = getElement().getTypeDefinition();
    return (def == null) ? EMPTY_ARRAY : def.getAttributes();
  }


  public boolean shouldBeSkippedByAnnotator() {
    return true;
  }
}
