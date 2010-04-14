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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene Zhuravlev
*         Date: Apr 13, 2010
*/
class AntDomDependentTargetReference extends AntDomReference {
  public AntDomDependentTargetReference(AntDomTarget target, String targetName, int start) {
    super(target, targetName, TextRange.from(start, start + targetName.length()));
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntDomTarget hostTarget = getAntElement();
    final XmlAttribute dependsAttribute = hostTarget.getDependsList().getXmlAttribute();
    if (dependsAttribute != null) {
      final TextRange refRange = getRangeInElement();
      final int start = refRange.getStartOffset();
      final String value = hostTarget.getDependsList().getValue();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        if (start > 0) {
          builder.append(value.substring(0, start));
        }
        builder.append(newElementName);
        if (value.length() > start + refRange.getLength()) {
          builder.append(value.substring(start + refRange.getLength()));
        }
        dependsAttribute.setValue(builder.toString());
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    return getElement();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null; // todo
  }

  public AntDomTarget getAntElement() {
    return (AntDomTarget)super.getAntElement();
  }

  public PsiElement resolveInner() {
    // todo: handle imported targets
    final AntDomProject antProject = getAntElement().getAntProject();
    if (antProject == null) {
      return null;
    }
    final AntDomTarget resolved = antProject.getTarget(getCanonicalText());
    if (resolved == null) {
      return null;
    }
    return resolved.getXmlElement();
  }
}
