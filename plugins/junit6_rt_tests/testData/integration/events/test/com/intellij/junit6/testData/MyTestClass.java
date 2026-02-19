// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6.testData;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.DynamicTest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class MyTestClass {
  @Test
  void test1(TestReporter reporter) {
    Map<String, String> map = new LinkedHashMap<String, String>();
    map.put("key1", "value1");
    map.put("stdout", "out1");
    reporter.publishEntry(map);
    Assertions.assertAll("2 errors",
      () -> Assertions.assertEquals("expected1", "actual1", "message1"),
      () -> Assertions.assertEquals("expected2", "actual2", "message2")
    );
  }

  @TestFactory
  Stream<DynamicTest> brokenStream() {
    throw new IllegalStateException("broken");
  }

  @org.junit.jupiter.api.Disabled("container disabled")
  @TestFactory
  Stream<DynamicTest> brokenStreamDisabled() {
    return Stream.of();
  }

  @org.junit.jupiter.api.Disabled("disabled")
  @Test
  void disabledTest() {}
}
