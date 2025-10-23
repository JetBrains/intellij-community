// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6.testData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AnnotationsTestClass {
  @Test
  @DisplayName("test's method")
  void test1() {}
}
