// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomCustomElement extends AntDomElement{

  private XmlName myXmlName;

  public final @Nullable Class getDefinitionClass() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).lookupClass(getXmlName());
  }

  public final @Nullable AntDomNamedElement getDeclaringElement() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).getDeclaringElement(getXmlName());
  }

  public final @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getLoadError() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).lookupError(getXmlName());
  }

  public final XmlName getXmlName() {
    if (myXmlName == null) {
      myXmlName = new XmlName(getXmlElementName(), getXmlElementNamespace());
    }
    return myXmlName;
  }
}
