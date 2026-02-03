// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.TestCaseLoader;

/**
 * Test classes marked with this interface are not seeded in buckets in the {@link TestCaseLoader#addClassIfTestCase(Class, String) normal way}.
 * Such classes are supposed to seed tests by themselves with the
 * {@link TestCaseLoader#shouldBucketTests()} and {@link TestCaseLoader#matchesCurrentBucket(String)} methods.
 */
public interface SelfSeedingTestCase {
  static void assertParentOf(Class<?> clazz) {
    assert SelfSeedingTestCase.class.isAssignableFrom(clazz) :
      "To make tests balancing work properly on the TeamCity, the test class should implement " + SelfSeedingTestCase.class.getName();
  }
}
