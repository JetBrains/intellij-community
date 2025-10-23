// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.example.impl;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class NavTest {
  @Test
  void methodNavigation() {}

  @TestFactory
  Collection<DynamicTest> fileSourceDynamicTests() {
    List<DynamicTest> list = new ArrayList<>();
    try {
      // Create a temporary file to force FileSource in JUnit Platform
      URI uri = Files.createTempFile("navtest", ".txt").toUri();
      list.add(dynamicTest("fileSource", uri, () -> {}));
    } catch (Exception ignored) {
    }
    return list;
  }
}
