// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface BucketingScheme {
  boolean matchesCurrentBucket(@NotNull String testIdentifier);

  default void initialize() {
  }
}
