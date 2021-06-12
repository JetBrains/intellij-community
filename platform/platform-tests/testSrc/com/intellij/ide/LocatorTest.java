// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.idea.ApplicationLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import junit.framework.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class LocatorTest extends TestCase {
  public void test() throws IOException {
    ApplicationLoader.createAppLocatorFile();
    Path locatorFile = Path.of(PathManager.getSystemPath() + "/" + ApplicationEx.LOCATOR_FILE_NAME);
    try {
      assertThat(locatorFile).isRegularFile();
      assertThat(locatorFile).isReadable();
      assertThat(locatorFile).isNotEmptyFile();
      assertThat(Files.readString(locatorFile, StandardCharsets.UTF_8)).isEqualTo(PathManager.getHomePath());
    }
    finally {
      Files.deleteIfExists(locatorFile);
    }
  }
}