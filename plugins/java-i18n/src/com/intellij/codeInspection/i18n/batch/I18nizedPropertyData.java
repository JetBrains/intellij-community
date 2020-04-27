// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.batch;

import org.jetbrains.annotations.NotNull;

public class I18nizedPropertyData<D> {
  private final String myKey;
  private final String myValue;
  private final boolean myMarkAsNonNls;
  private final D myContextData;

  public I18nizedPropertyData(@NotNull String key, @NotNull String value, @NotNull D contextData) {
    this(key, value, contextData, false);
  }

  private I18nizedPropertyData(@NotNull String key, @NotNull String value, @NotNull D contextData, boolean markAsNonNls) {
    myKey = key;
    myValue = value;
    myContextData = contextData;
    myMarkAsNonNls = markAsNonNls;
  }

  public String getKey() {
    return myKey;
  }

  public String getValue() {
    return myValue;
  }

  public D getContextData() {
    return myContextData;
  }

  public boolean isMarkAsNonNls() {
    return myMarkAsNonNls;
  }

  public I18nizedPropertyData<D> changeKey(@NotNull String newKey) {
    return new I18nizedPropertyData<>(newKey, myValue, myContextData, myMarkAsNonNls);
  }

  public I18nizedPropertyData<D> setMarkAsNonNls(boolean markAsNonNls) {
    return new I18nizedPropertyData<>(myKey, myValue, myContextData, markAsNonNls);
  }
}
