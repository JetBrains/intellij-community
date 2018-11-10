/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.beans;

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageDescriptor implements Comparable<UsageDescriptor> {
  private final String myKey;
  private final int myValue;
  @Nullable
  private final FUSUsageContext myContext;

  public UsageDescriptor(@NotNull String key) {
    this(key, 1);
  }

  public UsageDescriptor(@NotNull String key, int value) {
    this(key, value, (FUSUsageContext)null);
  }

  public UsageDescriptor(@NotNull String key, int value, @NotNull String... contextData) {
    this(key, value, contextData.length > 0 ? FUSUsageContext.create(contextData) : null);
  }

  public UsageDescriptor(@NotNull String key, int value, @Nullable FUSUsageContext context) {
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
    myContext = context;
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  @Nullable
  public FUSUsageContext getContext() {
    return myContext;
  }

  public int compareTo(UsageDescriptor ud) {
    return getKey().compareTo(ud.myKey);
  }

  @Override
  public String toString() {
    return myKey + "=" + myValue;
  }
}
