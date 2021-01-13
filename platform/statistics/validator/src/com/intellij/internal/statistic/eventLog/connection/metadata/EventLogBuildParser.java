// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import org.jetbrains.annotations.Nullable;

/**
 * Used to validate if events from this build are accepted
 * @param <T> object that represents build number
 * @see EventGroupsFilterRules#accepts(String, String, String)
 */
public interface EventLogBuildParser<T extends Comparable<T>> {
  @Nullable T parse(@Nullable String build);
}
