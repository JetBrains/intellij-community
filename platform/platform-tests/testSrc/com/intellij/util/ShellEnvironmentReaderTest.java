// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.util.system.OS;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(30)
public class ShellEnvironmentReaderTest {
  private static final int TIMEOUT_MILLIS = 10_000;

  @Test void userShellEnv() throws IOException {
    assumeFalse(OS.CURRENT == OS.Windows);

    var command = ShellEnvironmentReader.shellCommand(null, null, null);
    var markerName = "__ShellEnvironmentReaderTest_MARKER__";
    var markerValue = String.valueOf(new Random().nextLong());
    command.environment().put(markerName, markerValue);
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry(markerName, markerValue)
      .hasSizeGreaterThanOrEqualTo(System.getenv().size() / 2);
  }

  @Test void standardShellEnv(@TempDir Path tempDir) throws IOException {
    assumeFalse(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.sh"), "export FOO_TEST_1=\"123\"\nexport FOO_TEST_2=\"$1\"");
    var command = ShellEnvironmentReader.shellCommand("/bin/sh", file, List.of("arg_value"));
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry("FOO_TEST_1", "123")
      .containsEntry("FOO_TEST_2", "arg_value");
  }

  @Test void standardShellEnvErrorHandling(@TempDir Path tempDir) throws Exception {
    assumeFalse(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.sh"), "echo some error\nexit 1");
    var command = ShellEnvironmentReader.shellCommand("/bin/sh", file, null);
    assertThatThrownBy(() -> ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS))
      .extracting(ShellEnvironmentReaderTest::collectTextAndAttachment, InstanceOfAssertFactories.STRING)
      .contains("some error");
  }

  @Test void standardShellReaderTermination(@TempDir Path tempDir) throws IOException {
    assumeFalse(OS.CURRENT == OS.Windows);

    var timeout = 1000;
    var file = Files.writeString(tempDir.resolve("test.sh"), "sleep " + timeout * 5 / 1000);
    var command = ShellEnvironmentReader.shellCommand("/bin/sh", file, List.of("arg_value"));
    assertThatThrownBy(() -> ShellEnvironmentReader.readEnvironment(command, timeout))
      .isInstanceOf(IOException.class);
  }

  @Test void winShellEnv() throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var command = ShellEnvironmentReader.winShellCommand(null, null);
    var markerName = "__ShellEnvironmentReaderTest_MARKER__";
    var markerValue = String.valueOf(new Random().nextLong());
    command.environment().put(markerName, markerValue);
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry(markerName, markerValue)
      .hasSizeGreaterThanOrEqualTo(System.getenv().size() / 2);
  }

  @Test void winShellEnvFromFile(@TempDir Path tempDir) throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.bat"), "set FOO_TEST_1=123\r\nset FOO_TEST_2=%1");
    var command = ShellEnvironmentReader.winShellCommand(file, List.of("arg_value"));
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry("FOO_TEST_1", "123")
      .containsEntry("FOO_TEST_2", "arg_value");
  }

  @Test void powerShellEnv() throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var command = ShellEnvironmentReader.powerShellCommand(null, null);
    var markerName = "__ShellEnvironmentReaderTest_MARKER__";
    var markerValue = String.valueOf(new Random().nextLong());
    command.environment().put(markerName, markerValue);
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry(markerName, markerValue)
      .hasSizeGreaterThanOrEqualTo(System.getenv().size() / 2);
  }

  @Test void powerShellEnvFromFile(@TempDir Path tempDir) throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.ps1"), "$env:FOO_TEST_1=\"123\"\r\n$env:FOO_TEST_2=$($args[0])");
    var command = ShellEnvironmentReader.powerShellCommand(file, List.of("arg_value"));
    var result = ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS);
    assertThat(result.first)
      .containsEntry("FOO_TEST_1", "123")
      .containsEntry("FOO_TEST_2", "arg_value");
  }

  @Test void winShellEnvErrorHandling(@TempDir Path tempDir) throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.bat"), "echo some error\r\nexit /B 1");
    var command = ShellEnvironmentReader.winShellCommand(file, null);
    assertThatThrownBy(() -> ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS))
      .extracting(ShellEnvironmentReaderTest::collectTextAndAttachment, InstanceOfAssertFactories.STRING)
      .contains("some error");
  }

  @Test void powerShellEnvErrorHandling(@TempDir Path tempDir) throws Exception {
    assumeTrue(OS.CURRENT == OS.Windows);

    var file = Files.writeString(tempDir.resolve("test.ps1"), "echo \"some failure\"\r\nWrite-Error \"some error\"\r\nexit 100");
    var command = ShellEnvironmentReader.powerShellCommand(file, null);
    assertThatThrownBy(() -> ShellEnvironmentReader.readEnvironment(command, TIMEOUT_MILLIS))
      .extracting(ShellEnvironmentReaderTest::collectTextAndAttachment, InstanceOfAssertFactories.STRING)
      .contains("some error")
      .contains("some failure");
  }

  private static String collectTextAndAttachment(Throwable e) {
    var errorTextBuilder = new StringBuilder(e.getMessage());
    if (e instanceof ExceptionWithAttachments ewa) {
      Stream.of(ewa.getAttachments()).map(Attachment::getDisplayText).forEach(errorTextBuilder::append);
    }
    return errorTextBuilder.toString();
  }
}
