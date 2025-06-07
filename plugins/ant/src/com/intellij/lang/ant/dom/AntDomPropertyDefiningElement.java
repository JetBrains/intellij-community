// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomPropertyDefiningElement extends AntDomElement implements PropertiesProvider {

  @Override
  public final @NotNull Iterator<String> getNamesIterator() {
    final List<GenericAttributeValue<String>> attribs = getPropertyDefiningAttributes();
    final List<String> result = new ArrayList<>(attribs.size());
    for (GenericAttributeValue<String> attribValue : attribs) {
      final String name = attribValue.getStringValue();
      if (name != null && !name.isEmpty()) {
        result.add(name);
      }
    }
    result.addAll(getImplicitPropertyNames());
    return result.iterator();
  }

  @Override
  public final PsiElement getNavigationElement(String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (!propertyName.equals(value.getStringValue())) {
        continue;
      }
      final DomTarget domTarget = DomTarget.getTarget(this, value);
      return domTarget != null? PomService.convertToPsi(domTarget) : null;
    }

    for (String propName : getImplicitPropertyNames()) {
      if (propertyName.equals(propName)) {
        final DomTarget domTarget = DomTarget.getTarget(this);
        if (domTarget != null) {
          return PomService.convertToPsi(domTarget);
        }
        final XmlElement xmlElement = getXmlElement();
        return xmlElement != null? xmlElement.getNavigationElement() : null;
      }
    }
    return null;
  }

  @Override
  public final String getPropertyValue(final String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (propertyName.equals(value.getStringValue())) {
        return calcPropertyValue(propertyName);
      }
    }
    for (String implicitPropName : getImplicitPropertyNames()) {
      if (propertyName.equals(implicitPropName)) {
        return calcPropertyValue(propertyName);
      }
    }
    return null;
  }

  protected List<GenericAttributeValue<String>> getPropertyDefiningAttributes() {
    return Collections.emptyList();
  }

  protected List<String> getImplicitPropertyNames() {
    return Collections.emptyList();
  }

  protected @NlsSafe String calcPropertyValue(@NonNls String propertyName) {
    return ""; // some non-null value; actual value can be determined at runtime only
  }
}
