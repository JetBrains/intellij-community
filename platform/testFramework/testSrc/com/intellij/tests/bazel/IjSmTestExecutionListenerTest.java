// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

class IjSmTestExecutionListenerTest {
  private static final String EXPECTED_FILE_PROPERTY = "intellij.test.ij.sm.listener.expected.file";

  @Test
  void comparisonFailureCarriesExpectedFilePath(@TempDir Path tempDir) throws IOException {
    Path baselineFile = Files.writeString(tempDir.resolve("baseline.txt"), "expected\n");
    String output = runSampleTestCapturingOutput(baselineFile);
    assertThat(output).contains("type='comparisonFailure'");
    assertThat(output).contains("expected='expected|n'");
    assertThat(output).contains("actual='actual|n'");
    assertThat(output).contains("expectedFile='" + baselineFile.toRealPath() + "'");
  }

  private static String runSampleTestCapturingOutput(Path baselineFile) {
    ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setProperty(EXPECTED_FILE_PROPERTY, baselineFile.toString());
      System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
      Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
                                                   .enableLauncherSessionListenerAutoRegistration(false)
                                                   .build());
      launcher.execute(request().selectors(selectClass(FileComparisonSampleTest.class)).build(), new IjSmTestExecutionListener());
    }
    finally {
      System.setOut(originalOut);
      System.clearProperty(EXPECTED_FILE_PROPERTY);
    }
    return capturedOut.toString(StandardCharsets.UTF_8);
  }

  public static class FileComparisonSampleTest {
    @Test
    @EnabledIfSystemProperty(named = EXPECTED_FILE_PROPERTY, matches = ".+")
    void fileComparisonFailure() {
      Path file = Paths.get(System.getProperty(EXPECTED_FILE_PROPERTY));
      String expected;
      try {
        expected = Files.readString(file);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      throw new FileComparisonFailedError("baseline mismatch", expected, "actual\n", file.toString());
    }
  }
}
