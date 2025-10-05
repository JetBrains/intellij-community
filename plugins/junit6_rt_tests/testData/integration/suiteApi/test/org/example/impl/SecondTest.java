// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.example.impl;

import org.junit.jupiter.api.Test;

public class SecondTest {
  @Test
  public void test1() {
  }

  @Test
  public void test2() {
    throw new RuntimeException();
  }
}