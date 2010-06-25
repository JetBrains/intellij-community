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

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
public abstract class AntDomElement implements DomElement {

  @Attribute("id")
  public abstract GenericAttributeValue<String> getId();

  @Attribute("refid")
  @Convert(value = AntDomRefIdConverter.class)
  public abstract GenericAttributeValue<AntDomElement> getRefId();

  public final AntDomProject getAntProject() {
    return getParentOfType(AntDomProject.class, false);
  }

  public final List<AntDomElement> getAntChildren() {
    return DomUtil.getDefinedChildrenOfType(this, AntDomElement.class, true, false);
  }

  public String toString() {
    final XmlTag tag = getXmlTag();
    if (tag == null) {
      return super.toString();
    }
    final String name = tag.getName();
    if ("".equals(name)) {
      return super.toString();
    }
    return name;
  }

}
