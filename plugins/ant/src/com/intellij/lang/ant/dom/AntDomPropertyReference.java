/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public class AntDomPropertyReference extends AntDomReference{
  private final DomElement myInvocationContextElement;

  public AntDomPropertyReference(DomElement invocationContextElement, XmlAttributeValue element, String text, TextRange textRange) {
    super(element, text, textRange);
    myInvocationContextElement = invocationContextElement;
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Override
  public PsiElement resolveInner() {
    final AntDomProject project = myInvocationContextElement.getParentOfType(AntDomProject.class, true);
    if (project != null) {
      return PropertyResolver.resolve(project.getContextAntProject(), getCanonicalText(), myInvocationContextElement).getFirst();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  private static String cutPrefix(final String text, final String prefix) {
    if (prefix != null && text.startsWith(prefix) && prefix.length() < text.length() && text.charAt(prefix.length()) == '.') {
      return text.substring(prefix.length() + 1);
    }
    return text;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final AntDomProject project = myInvocationContextElement.getParentOfType(AntDomProject.class, true);
    if (project != null) {
      final Collection<String> variants = PropertyResolver.resolve(project.getContextAntProject(), getCanonicalText(), myInvocationContextElement).getSecond();
       return variants.toArray(new String[variants.size()]);
    }
    return super.getVariants();
  }

}
