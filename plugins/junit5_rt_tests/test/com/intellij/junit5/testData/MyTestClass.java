// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.testData;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

@SuppressWarnings("NewClassNamingConvention")
public class MyTestClass {
  @Test
  void test1() {
  }

  @TestFactory
  Stream<DynamicTest> brokenStream() {
    return Stream.generate(() -> { throw new IllegalStateException(); });
  }
}
