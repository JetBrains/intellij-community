// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Objects;

@Tag("build-property")
public final class BuildFileProperty implements JDOMExternalizable, Cloneable {
  private static final @NonNls String NAME = "name";
  private static final @NonNls String VALUE = "value";
  private String myPropertyName;
  private String myPropertyValue;

  public BuildFileProperty() {
    this("", "");
  }

  public BuildFileProperty(String propertyName, String propertyValue) {
    setPropertyName(propertyName);
    myPropertyValue = propertyValue;
  }

  @Attribute(NAME)
  public String getPropertyName() {
    return myPropertyName;
  }

  public void setPropertyName(String propertyName) {
    myPropertyName = propertyName.trim();
  }

  @Attribute(VALUE)
  public String getPropertyValue() {
    return myPropertyValue;
  }

  public void setPropertyValue(String propertyValue) {
    myPropertyValue = propertyValue;
  }

  @Override
  public void readExternal(Element element) {
    myPropertyName = element.getAttributeValue(NAME);
    myPropertyValue = element.getAttributeValue(VALUE);
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NAME, getPropertyName());
    element.setAttribute(VALUE, getPropertyValue());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildFileProperty that = (BuildFileProperty)o;
    return Objects.equals(myPropertyName, that.myPropertyName) && Objects.equals(myPropertyValue, that.myPropertyValue);
  }

  @Override
  public int hashCode() {
    return 31 * (myPropertyName != null ? myPropertyName.hashCode() : 0)
              + (myPropertyValue != null ? myPropertyValue.hashCode() : 0);
  }

  @Override
  public BuildFileProperty clone() {
    try {
      return (BuildFileProperty)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
