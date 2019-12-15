// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.TestCaseLoader;

/**
 * Test classes marked with this interface are not seeded in buckets in {@link TestCaseLoader#addClassIfTestCase(java.lang.Class, java.lang.String) normal way}.
 * Such classes supposed to seed tests by themselves with
 * {@link TestCaseLoader#shouldBucketTests()} and {@link TestCaseLoader#matchesCurrentBucket(java.lang.String)} methods.
 */
public interface SelfSeedingTestCase {
}
