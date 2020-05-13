// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public abstract class LocalFileCustomWhiteListRule extends CustomWhiteListRule {
  private static final Logger LOG = Logger.getInstance(LocalFileCustomWhiteListRule.class);

  private WeakReference<CachedWhitelistedItems> myWhitelistRef;
  private final String myRuleId;
  private final Class myResourceHolder;
  private final String myRelativePath;

  protected LocalFileCustomWhiteListRule(@NotNull String ruleId, @NotNull Class resource, @NotNull String path) {
    myRuleId = ruleId;
    myResourceHolder = resource;
    myRelativePath = path;
  }

  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return myRuleId.equals(ruleId);
  }

  private boolean isWhitelisted(@NotNull String value) {
    final CachedWhitelistedItems whitelist = getWhitelist();
    return whitelist.contains(value);
  }

  @NotNull
  private synchronized CachedWhitelistedItems getWhitelist() {
    final CachedWhitelistedItems whitelist = SoftReference.dereference(myWhitelistRef);
    if (whitelist != null) {
      return whitelist;
    }
    final CachedWhitelistedItems items = create();
    myWhitelistRef = new WeakReference<>(items);
    return items;
  }

  @NotNull
  private CachedWhitelistedItems create() {
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      InputStream resourceStream = myResourceHolder.getResourceAsStream(myRelativePath);
      if (resourceStream == null) {
        throw new IOException("Resource " + myRelativePath + " not found");
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
        final List<String> values = FileUtil.loadLines(reader);
        if (!values.isEmpty()) {
          return CachedWhitelistedItems.create(ContainerUtil.map2SetNotNull(values, value -> createValue(value)));
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return CachedWhitelistedItems.empty();
  }

  @Nullable
  protected String createValue(String value) {
    return value.trim();
  }

  @NotNull
  @Override
  final protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data) || isWhitelisted(data)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  private static class CachedWhitelistedItems {
    private final Set<String> myValues;

    private CachedWhitelistedItems(@NotNull Set<String> values) {
      myValues = values;
    }

    public boolean contains(@NotNull String value) {
      return myValues.contains(value);
    }

    @NotNull
    public static CachedWhitelistedItems create(@NotNull Set<String> values) {
      return new CachedWhitelistedItems(values);
    }

    @NotNull
    public static CachedWhitelistedItems empty() {
      return new CachedWhitelistedItems(Collections.emptySet());
    }
  }
}
