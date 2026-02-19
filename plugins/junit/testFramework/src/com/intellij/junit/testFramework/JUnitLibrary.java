// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework;

public enum JUnitLibrary {
  JUNIT3(""),
  JUNIT4(""),
  HAMCREST(""),
  JUNIT5_7_0("5.7.0"),
  JUNIT5("5.14.1"),
  JUNIT6("6.0.0"),
  PIONEER("2.3.0");

  private final String version;
  JUnitLibrary(String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }
}
