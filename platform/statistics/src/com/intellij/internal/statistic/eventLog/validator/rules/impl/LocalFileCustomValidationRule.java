// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class LocalFileCustomValidationRule extends CustomWhiteListRule {
  private static final Logger LOG = Logger.getInstance(LocalFileCustomValidationRule.class);

  private WeakReference<CachedAllowedItems> myAllowedItemsRef;
  private final String myRuleId;
  private final Class myResourceHolder;
  private final String myRelativePath;

  protected LocalFileCustomValidationRule(@NotNull String ruleId, @NotNull Class resource, @NotNull String path) {
    myRuleId = ruleId;
    myResourceHolder = resource;
    myRelativePath = path;
  }

  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return myRuleId.equals(ruleId);
  }

  private boolean isAllowed(@NotNull String value) {
    final CachedAllowedItems allowed = getAllowedItems();
    return allowed.contains(value);
  }

  @NotNull
  private synchronized LocalFileCustomValidationRule.CachedAllowedItems getAllowedItems() {
    final CachedAllowedItems allowed = SoftReference.dereference(myAllowedItemsRef);
    if (allowed != null) {
      return allowed;
    }
    final CachedAllowedItems items = create();
    myAllowedItemsRef = new WeakReference<>(items);
    return items;
  }

  @NotNull
  private LocalFileCustomValidationRule.CachedAllowedItems create() {
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      InputStream resourceStream = myResourceHolder.getResourceAsStream(myRelativePath);
      if (resourceStream == null) {
        throw new IOException("Resource " + myRelativePath + " not found");
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
        final List<String> values = FileUtil.loadLines(reader);
        if (!values.isEmpty()) {
          return CachedAllowedItems.create(ContainerUtil.map2SetNotNull(values, value -> createValue(value)));
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return CachedAllowedItems.empty();
  }

  @Nullable
  protected String createValue(String value) {
    return value.trim();
  }

  @NotNull
  @Override
  final protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data) || isAllowed(data)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  private static final class CachedAllowedItems {
    private final Set<String> myValues;

    private CachedAllowedItems(@NotNull Set<String> values) {
      myValues = values;
    }

    public boolean contains(@NotNull String value) {
      return myValues.contains(value);
    }

    @NotNull
    public static LocalFileCustomValidationRule.CachedAllowedItems create(@NotNull Set<String> values) {
      return new CachedAllowedItems(values);
    }

    @NotNull
    public static LocalFileCustomValidationRule.CachedAllowedItems empty() {
      return new CachedAllowedItems(Collections.emptySet());
    }
  }
}
