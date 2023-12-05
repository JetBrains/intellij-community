// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing;

import com.intellij.TestCaseLoader;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class HashingBucketingScheme implements BucketingScheme {

  @Override
  public boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    return matchesCurrentBucketViaHashing(testIdentifier);
  }

  public static boolean matchesCurrentBucketViaHashing(@NotNull String testIdentifier) {
    return TestCaseLoader.TEST_RUNNERS_COUNT == 1 ||
           MathUtil.nonNegativeAbs(testIdentifier.hashCode()) % TestCaseLoader.TEST_RUNNERS_COUNT == TestCaseLoader.TEST_RUNNER_INDEX;
  }
}
