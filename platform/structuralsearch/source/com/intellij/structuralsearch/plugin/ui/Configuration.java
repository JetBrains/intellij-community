// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class Configuration implements JDOMExternalizable, Comparable<Configuration> {
  public static final Configuration[] EMPTY_ARRAY = {};
  @NonNls protected static final String NAME_ATTRIBUTE_NAME = "name";
  @NonNls private static final String CREATED_ATTRIBUTE_NAME = "created";

  private String name;
  private String category;
  private boolean predefined;
  private long created;

  public Configuration() {
    name = "";
    category = "";
    created = -1L;
  }

  public Configuration(String name, String category) {
    this.name = name;
    this.category = category;
    created = -1L;
  }

  protected Configuration(Configuration configuration) {
    name = configuration.name;
    category = configuration.category;
    created = configuration.created;
    predefined = false; // copy is never predefined
  }

  public abstract Configuration copy();

  public String getName() {
    return name;
  }

  public void setName(@NotNull String value) {
    name = value;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(@NotNull String category) {
    this.category = category;
  }

  public long getCreated() {
    return created;
  }

  public void setCreated(long created) {
    if (predefined) {
      throw new AssertionError();
    }
    this.created = created;
  }

  @Override
  public void readExternal(Element element) {
    name = element.getAttributeValue(NAME_ATTRIBUTE_NAME);
    final Attribute attribute = element.getAttribute(CREATED_ATTRIBUTE_NAME);
    if (attribute != null) {
      try {
        created = attribute.getLongValue();
      }
      catch (DataConversionException ignore) {}
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NAME_ATTRIBUTE_NAME,name);
    if (created > 0) {
      element.setAttribute(CREATED_ATTRIBUTE_NAME, String.valueOf(created));
    }
  }

  public boolean isPredefined() {
    return predefined;
  }

  public void setPredefined(boolean predefined) {
    this.predefined = predefined;
  }

  public abstract MatchOptions getMatchOptions();

  public abstract NamedScriptableDefinition findVariable(String name);

  @Override
  public int compareTo(Configuration other) {
    int result = StringUtil.naturalCompare(getCategory(), other.getCategory());
    return result != 0 ? result : StringUtil.naturalCompare(getName(), other.getName());
  }

  public boolean equals(Object configuration) {
    if (!(configuration instanceof Configuration)) return false;
    Configuration other = (Configuration)configuration;
    if (category != null ? !category.equals(other.category) : other.category != null) {
      return false;
    }
    return name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + (category != null ? category.hashCode() : 0);
  }

  @NonNls public static final String CONTEXT_VAR_NAME = "__context__";
}
