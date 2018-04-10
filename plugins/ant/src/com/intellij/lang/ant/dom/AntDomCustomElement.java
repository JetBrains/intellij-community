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

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomCustomElement extends AntDomElement{

  private XmlName myXmlName;

  @Nullable
  public final Class getDefinitionClass() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).lookupClass(getXmlName());
  }

  @Nullable
  public final AntDomNamedElement getDeclaringElement() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).getDeclaringElement(getXmlName());
  }

  @Nullable
  public final String getLoadError() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).lookupError(getXmlName());
  }

  public final  XmlName getXmlName() {
    if (myXmlName == null) {
      myXmlName = new XmlName(getXmlElementName(), getXmlElementNamespace());
    }
    return myXmlName;
  }
}
