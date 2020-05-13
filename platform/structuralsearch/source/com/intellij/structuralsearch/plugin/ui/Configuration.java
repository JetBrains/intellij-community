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
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public abstract class Configuration implements JDOMExternalizable, Comparable<Configuration> {
  @NonNls public static final String CONTEXT_VAR_NAME = "__context__";

  public static final Configuration[] EMPTY_ARRAY = {};

  @NonNls protected static final String NAME_ATTRIBUTE_NAME = "name";
  @NonNls private static final String CREATED_ATTRIBUTE_NAME = "created";
  @NonNls private static final String UUID_ATTRIBUTE_NAME = "uuid";
  @NonNls private static final String DESCRIPTION_ATTRIBUTE_NAME = "description";
  @NonNls private static final String SUPPRESS_ID_ATTRIBUTE_NAME = "suppressId";
  @NonNls private static final String PROBLEM_DESCRIPTOR_ATTRIBUTE_NAME = "problemDescriptor";
  @NonNls private static final String ORDER_ATTRIBUTE_NAME = "order";

  private String name;
  private String category;
  private boolean predefined;
  private long created;
  private UUID uuid;
  private String description;
  private String suppressId;
  private String problemDescriptor;
  private int order;

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
    description = configuration.description;
    suppressId = configuration.suppressId;
    problemDescriptor = configuration.problemDescriptor;
    order = configuration.order;
  }

  public abstract Configuration copy();

  @NotNull
  public String getName() {
    return name;
  }

  public void setName(@NotNull String value) {
    if (uuid == null) {
      uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
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

  @NotNull
  public UUID getUuid() {
    return uuid == null ? (uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))) : uuid;
  }

  public void setUuid(@Nullable UUID uuid) {
    this.uuid = uuid;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSuppressId() {
    return suppressId;
  }

  public void setSuppressId(String suppressId) {
    this.suppressId = suppressId;
  }

  public String getProblemDescriptor() {
    return this.problemDescriptor;
  }

  public void setProblemDescriptor(String problemDescriptor) {
    this.problemDescriptor = problemDescriptor;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    if (order < 0) throw new IllegalArgumentException();
    this.order = order;
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
    final Attribute descriptionAttribute = element.getAttribute(DESCRIPTION_ATTRIBUTE_NAME);
    if (descriptionAttribute != null) {
      description = descriptionAttribute.getValue();
    }
    final Attribute suppressIdAttribute = element.getAttribute(SUPPRESS_ID_ATTRIBUTE_NAME);
    if (suppressIdAttribute != null) {
      suppressId = suppressIdAttribute.getValue();
    }
    final Attribute problemDescriptorAttribute = element.getAttribute(PROBLEM_DESCRIPTOR_ATTRIBUTE_NAME);
    if (problemDescriptorAttribute != null) {
      problemDescriptor = problemDescriptorAttribute.getValue();
    }
    final Attribute mainAttribute = element.getAttribute(ORDER_ATTRIBUTE_NAME);
    if (mainAttribute != null) {
      try {
        order = Math.max(0, mainAttribute.getIntValue());
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
    if (uuid != null && !uuid.equals(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)))) {
      element.setAttribute(UUID_ATTRIBUTE_NAME, uuid.toString());
    }
    if (!StringUtil.isEmpty(description)) {
      element.setAttribute(DESCRIPTION_ATTRIBUTE_NAME, description);
    }
    if (!StringUtil.isEmpty(suppressId)) {
      element.setAttribute(SUPPRESS_ID_ATTRIBUTE_NAME, suppressId);
    }
    if (!StringUtil.isEmpty(problemDescriptor)) {
      element.setAttribute(PROBLEM_DESCRIPTOR_ATTRIBUTE_NAME, problemDescriptor);
    }
    if (order != 0) {
      element.setAttribute(ORDER_ATTRIBUTE_NAME, String.valueOf(order));
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
