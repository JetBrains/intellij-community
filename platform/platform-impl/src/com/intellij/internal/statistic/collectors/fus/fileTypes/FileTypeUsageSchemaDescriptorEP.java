// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileTypeUsageSchemaDescriptorEP<T> extends BaseKeyedLazyInstance<T> implements KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  @Attribute("schema")
  public String schema;

  @Attribute("implementationClass")
  public String implementationClass;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull String getKey() {
    return schema;
  }
}