// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.request;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface StatsResponseProcessor<T> {
  T onSucceed(@NotNull StatsHttpResponse response) throws IOException, StatsResponseException;
}
