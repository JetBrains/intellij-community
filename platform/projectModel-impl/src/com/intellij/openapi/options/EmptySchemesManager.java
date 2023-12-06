// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.extensions.PluginDescriptor;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.Sequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class EmptySchemesManager extends SchemeManager<Object> {
  @Override
  public @NotNull Collection<Object> loadSchemes() {
    return Collections.emptySet();
  }

  @Override
  public void reload(@Nullable Function1<? super Object, Boolean> retainFilter) {
  }

  @Override
  public void addScheme(final @NotNull Object scheme, final boolean replaceExisting) {
  }

  @Override
  public @NotNull List<Object> getAllSchemes() {
    return Collections.emptyList();
  }

  @Override
  public Object findSchemeByName(@NotNull String schemeName) {
    return null;
  }

  @Override
  public @Nullable String getCurrentSchemeName() {
    return null;
  }

  @Override
  public boolean removeScheme(@NotNull Object scheme) {
    return false;
  }

  @Override
  public @NotNull Collection<String> getAllSchemeNames() {
    return Collections.emptySet();
  }

  @Override
  public @NotNull File getRootDirectory() {
    //noinspection ConstantConditions
    return null;
  }

  @Override
  public void setCurrentSchemeName(@Nullable String schemeName, boolean notify) {
  }

  @Override
  public void setCurrentSchemeName(@Nullable String s) {
  }

  @Override
  public @Nullable Scheme getActiveScheme() {
    return null;
  }

  @Override
  public @Nullable Object removeScheme(@NotNull String name) {
    return null;
  }

  @Override
  public Object loadBundledScheme(@NotNull String resourceName, @Nullable Object requestor, @Nullable PluginDescriptor pluginDescriptor) {
    return null;
  }

  @Override
  public boolean isMetadataEditable(Object scheme) {
    return true;
  }

  @Override
  public void save() {
  }

  @Override
  public @NotNull SettingsCategory getSettingsCategory() {
    return SettingsCategory.OTHER;
  }

  @Override
  public void loadBundledSchemes(@NotNull Sequence<? extends LoadBundleSchemeRequest<Object>> providers) {
  }
}
