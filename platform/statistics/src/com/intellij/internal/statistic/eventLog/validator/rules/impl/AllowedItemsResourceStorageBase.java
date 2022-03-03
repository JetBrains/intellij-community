// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AllowedItemsResourceStorageBase {
  private static final Logger LOG = Logger.getInstance(AllowedItemsResourceStorageBase.class);
  private final Class<?> resourceHolder;
  private final String relativePath;

  public AllowedItemsResourceStorageBase(@NotNull Class<?> holder, @NotNull String path) {
    resourceHolder = holder;
    relativePath = path;
  }

  @Nullable
  protected String createValue(@NotNull String value) {
    return value.trim();
  }

  @NotNull
  protected Set<String> readItems() {
    try {
      InputStream resourceStream = resourceHolder.getResourceAsStream(relativePath);
      if (resourceStream == null) {
        throw new IOException("Resource " + relativePath + " not found");
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
        final List<String> values = FileUtil.loadLines(reader);
        if (!values.isEmpty()) {
          return ContainerUtil.map2SetNotNull(values, s -> createValue(s));
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return Collections.emptySet();
  }

  @NotNull
  public abstract Set<String> getItems();
}