// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.util.ObjectUtils;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public abstract class Configuration implements JDOMExternalizable {
  public static final @NonNls String CONTEXT_VAR_NAME = "__context__";

  public static final Configuration[] EMPTY_ARRAY = {};

  protected static final @NonNls String NAME_ATTRIBUTE_NAME = "name";
  private static final @NonNls String CREATED_ATTRIBUTE_NAME = "created";
  private static final @NonNls String UUID_ATTRIBUTE_NAME = "uuid";
  private static final @NonNls String DESCRIPTION_ATTRIBUTE_NAME = "description";
  private static final @NonNls String SUPPRESS_ID_ATTRIBUTE_NAME = "suppressId";
  private static final @NonNls String PROBLEM_DESCRIPTOR_ATTRIBUTE_NAME = "problemDescriptor";
  private static final @NonNls String CLEANUP_ATTRIBUTE_NAME = "cleanup";
  private static final @NonNls String ORDER_ATTRIBUTE_NAME = "order";

  private @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name;
  private String category;
  private boolean predefined;
  private long created;
  private String uuid;
  private String description;
  private String suppressId;
  private String problemDescriptor;
  private boolean cleanup;
  private int order;

  /**
   * String used to refer to this configuration. It should be unique or null.
   *  - For predefined configurations, the refName is a unique String
   *  - For user-defined configurations, the refName is null and getRefName returns the template name
   */
  private @NonNls String refName;

  public Configuration() {
    name = "";
    category = "";
    created = -1L;
  }

  public Configuration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name, @NotNull String category) {
    this.name = name;
    this.category = category;
    created = -1L;
  }

  protected Configuration(@NotNull Configuration configuration) {
    name = configuration.name;
    category = configuration.category;
    created = -1L; // receives timestamp when added to history
    predefined = false; // copy is never predefined
    uuid = configuration.uuid;
    description = configuration.description;
    suppressId = configuration.suppressId;
    problemDescriptor = configuration.problemDescriptor;
    cleanup = configuration.cleanup;
    order = configuration.order;
    refName = null; // copy never has a refName
  }

  public abstract @NotNull Configuration copy();

  public @NotNull @Nls String getName() {
    return name;
  }

  public void setName(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String value) {
    if (uuid == null) {
      getUuid();
    }
    name = value;
  }

  public @NotNull @Nls String getTypeText() {
    final LanguageFileType type = getFileType();
    final String name = type == null ? CoreBundle.message("filetype.unknown.display.name") : type.getLanguage().getDisplayName();
    return isPredefined() ? SSRBundle.message("predefined.configuration.type.text", name)
                          : SSRBundle.message("predefined.configuration.type.text.user.defined", name);
  }

  public @NotNull String getCategory() {
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

  public @NotNull String getUuid() {
    if (uuid == null) {
      uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
    return uuid;
  }

  public void setUuid(@Nullable String uuid) {
    this.uuid = uuid;
  }

  public @NlsSafe @Nullable String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public @NlsSafe @Nullable String getSuppressId() {
    return suppressId;
  }

  public void setSuppressId(String suppressId) {
    this.suppressId = suppressId;
  }

  public @NlsSafe @Nullable String getProblemDescriptor() {
    return problemDescriptor;
  }

  public void setProblemDescriptor(String problemDescriptor) {
    this.problemDescriptor = problemDescriptor;
  }

  public boolean isCleanup() {
    return cleanup;
  }

  public void setCleanup(boolean cleanup) {
    this.cleanup = cleanup;
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
    //noinspection HardCodedStringLiteral
    name = ObjectUtils.notNull(element.getAttributeValue(NAME_ATTRIBUTE_NAME), "");
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
        uuid = uuidAttribute.getValue();
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
    Attribute attribute = element.getAttribute(CLEANUP_ATTRIBUTE_NAME);
    if (attribute != null) {
      try {
        cleanup = attribute.getBooleanValue();
      }
      catch (DataConversionException e) {
        cleanup = false;
      }
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
    if (uuid != null && !uuid.equals(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString())) {
      element.setAttribute(UUID_ATTRIBUTE_NAME, uuid);
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
    if (cleanup) {
      element.setAttribute(CLEANUP_ATTRIBUTE_NAME, String.valueOf(cleanup));
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

  public abstract @NotNull MatchOptions getMatchOptions();

  public abstract @NotNull ReplaceOptions getReplaceOptions();

  public abstract NamedScriptableDefinition findVariable(@NotNull String name);

  public abstract void removeUnusedVariables();

  @Override
  public boolean equals(Object configuration) {
    if (!(configuration instanceof Configuration other)) return false;
    return Objects.equals(category, other.category) && name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + (category != null ? category.hashCode() : 0);
  }

  public @NotNull Icon getIcon() {
    final LanguageFileType type = getFileType();
    return (type == null || type.getIcon() == null) ? AllIcons.FileTypes.Unknown : type.getIcon();
  }

  public @Nullable LanguageFileType getFileType() {
    return getMatchOptions().getFileType();
  }

  public @NotNull @NonNls String getRefName() {
    return refName == null || !predefined ? name : refName;
  }

  public void setRefName(String refName) {
    if (predefined)
      this.refName = refName;
  }
}
