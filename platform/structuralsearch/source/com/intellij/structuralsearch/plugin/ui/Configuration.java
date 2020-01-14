// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public abstract class Configuration implements JDOMExternalizable, Comparable<Configuration> {
  @NonNls public static final String CONTEXT_VAR_NAME = "__context__";

  public static final Configuration[] EMPTY_ARRAY = {};
  @NonNls protected static final String NAME_ATTRIBUTE_NAME = "name";
  @NonNls private static final String CREATED_ATTRIBUTE_NAME = "created";
  @NonNls private static final String UUID_ATTRIBUTE_NAME = "uuid";

  private String name;
  private String category;
  private boolean predefined;
  private long created;
  private UUID uuid;

  private transient String myCurrentVariableName = null;

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
    created = -1L; // receives timestamp when added to history
    predefined = false; // copy is never predefined
    uuid = configuration.uuid;
  }

  public abstract Configuration copy();

  @NotNull
  public String getName() {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return StringUtil.collapseWhiteSpace(getMatchOptions().getSearchPattern());
    }
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

  public void resetUuid() {
    uuid = UUID.randomUUID();
  }

  @NotNull
  public UUID getUuid() {
    return uuid == null ? (uuid = UUID.randomUUID()) : uuid;
  }

  @Override
  public void readExternal(Element element) {
    name = element.getAttributeValue(NAME_ATTRIBUTE_NAME);
    final Attribute createdAttribute = element.getAttribute(CREATED_ATTRIBUTE_NAME);
    if (createdAttribute != null) {
      try {
        created = createdAttribute.getLongValue();
      }
      catch (DataConversionException ignore) {}
    }
    final Attribute uuidAttribute = element.getAttribute(UUID_ATTRIBUTE_NAME);
    if (uuidAttribute != null) {
      try {
        uuid = UUID.fromString(uuidAttribute.getValue());
      }
      catch (IllegalArgumentException ignore) {}
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NAME_ATTRIBUTE_NAME,name);
    if (created > 0) {
      element.setAttribute(CREATED_ATTRIBUTE_NAME, String.valueOf(created));
    }
    if (uuid != null) {
      element.setAttribute(UUID_ATTRIBUTE_NAME, uuid.toString());
    }
  }

  public boolean isPredefined() {
    return predefined;
  }

  public void setPredefined(boolean predefined) {
    this.predefined = predefined;
  }

  public abstract MatchOptions getMatchOptions();

  public ReplaceOptions getReplaceOptions() {
    return null;
  }

  public abstract NamedScriptableDefinition findVariable(String name);

  public abstract void removeUnusedVariables();

  public String getCurrentVariableName() {
    return myCurrentVariableName;
  }

  public void setCurrentVariableName(String variableName) {
    myCurrentVariableName = variableName;
  }

  @Override
  public int compareTo(Configuration other) {
    final int result = StringUtil.naturalCompare(getCategory(), other.getCategory());
    return result != 0 ? result : StringUtil.naturalCompare(getName(), other.getName());
  }

  public boolean equals(Object configuration) {
    if (!(configuration instanceof Configuration)) return false;
    final Configuration other = (Configuration)configuration;
    return Objects.equals(category, other.category) && name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + (category != null ? category.hashCode() : 0);
  }
}
