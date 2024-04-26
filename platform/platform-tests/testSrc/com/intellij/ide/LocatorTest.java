// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.platform.ide.bootstrap.ApplicationLoader;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class LocatorTest {
  @Test
  public void test() throws IOException {
    //noinspection KotlinInternalInJava
    ApplicationLoader.createAppLocatorFile();

    Path locatorFile = Path.of(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME);
    try {
      assertThat(locatorFile)
        .isRegularFile()
        .isReadable()
        .usingCharset(StandardCharsets.UTF_8).hasContent(PathManager.getHomePath());
    }
    finally {
      Files.deleteIfExists(locatorFile);
    }
  }
}
