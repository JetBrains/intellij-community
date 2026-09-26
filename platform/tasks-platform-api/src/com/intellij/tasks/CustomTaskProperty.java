// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Tag("property")
public class CustomTaskProperty {

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("displayName")
  private @Nls String displayName;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("value")
  private @NotNull String value;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("iconUrl")
  private @Nullable String iconUrl;

  @SuppressWarnings("unused")
  public CustomTaskProperty() {
    this("", "", null);
  }

  public CustomTaskProperty(@Nls String displayName, @NotNull String value, @Nullable String iconUrl) {
    this.displayName = displayName;
    this.value = value;
    this.iconUrl = iconUrl;
  }

  public @Nls String getDisplayName() {
    return displayName;
  }

  public @NotNull String getValue() {
    return value;
  }

  public @Nullable String getIconUrl() {
    return iconUrl;
  }
}