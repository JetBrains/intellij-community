// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.devkit.dom.Extension;

import java.util.Objects;

public class ExtensionPointCandidate extends PointableCandidate {
  public final String epName;
  public final String attributeName;
  public final String tagName;
  public final String beanClassName;

  public ExtensionPointCandidate(SmartPsiElementPointer<XmlTag> pointer,
                                 String epName,
                                 String attributeName,
                                 String tagName,
                                 String beanClassName) {
    super(pointer);
    this.epName = epName;
    this.attributeName = attributeName;
    this.tagName = tagName;
    this.beanClassName = beanClassName;
  }

  public ExtensionPointCandidate(SmartPsiElementPointer<XmlTag> pointer,
                                 String epName) {
    super(pointer);
    this.epName = epName;
    this.attributeName = Extension.IMPLEMENTATION_ATTRIBUTE;
    this.tagName = null;
    this.beanClassName = null;
  }

  @Override
  public String toString() {
    return epName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExtensionPointCandidate candidate = (ExtensionPointCandidate)o;

    if (!Objects.equals(epName, candidate.epName)) return false;
    if (!Objects.equals(attributeName, candidate.attributeName)) return false;
    if (!Objects.equals(tagName, candidate.tagName)) return false;
    if (!Objects.equals(beanClassName, candidate.beanClassName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = epName != null ? epName.hashCode() : 0;
    result = 31 * result + (attributeName != null ? attributeName.hashCode() : 0);
    result = 31 * result + (tagName != null ? tagName.hashCode() : 0);
    result = 31 * result + (beanClassName != null ? beanClassName.hashCode() : 0);
    return result;
  }
}
