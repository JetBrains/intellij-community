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
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;

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
    this.attributeName = "implementation";
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

    if (epName != null ? !epName.equals(candidate.epName) : candidate.epName != null) return false;
    if (attributeName != null ? !attributeName.equals(candidate.attributeName) : candidate.attributeName != null) return false;
    if (tagName != null ? !tagName.equals(candidate.tagName) : candidate.tagName != null) return false;
    if (beanClassName != null ? !beanClassName.equals(candidate.beanClassName) : candidate.beanClassName != null) return false;

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
