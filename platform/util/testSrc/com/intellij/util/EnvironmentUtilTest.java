// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.IoTestUtil.assumeUnix;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.Assert.*;

public class EnvironmentUtilTest {
  @Test(timeout = 30000)
  public void map() {
    assertNotNull(EnvironmentUtil.getEnvironmentMap());
  }

  @Test
  public void path() {
    assertNotNull(EnvironmentUtil.getValue("PATH"));
    if (SystemInfo.isWindows) {
      assertNotNull(EnvironmentUtil.getValue("Path"));
    }
  }

  @Test
  public void parse() {
    String text = "V1=single line\0V2=multiple\nlines\0V3=single line\0PWD=?\0";
    Map<String, String> map = EnvironmentUtil.testParser(text);
    assertEquals("single line", map.get("V1"));
    assertEquals("multiple\nlines", map.get("V2"));
    assertEquals("single line", map.get("V3"));
    if (System.getenv().containsKey("PWD")) {
      assertEquals(System.getenv("PWD"), map.get("PWD"));
      assertEquals(4, map.size());
    }
    else {
      assertEquals(3, map.size());
    }
  }

  @Test(timeout = 30000)
  public void load() throws IOException {
    assumeUnix();

    Map<String, String> env = EnvironmentUtil.testLoader();
    assertTrue(env.size() >= System.getenv().size() / 2);
  }

  @Test(timeout = 30000)
  public void loadingBatEnv() throws Exception {
    assumeWindows();

    File file = FileUtil.createTempFile("test", ".bat", true);
    FileUtil.writeToFile(file, "set FOO_TEST_1=123\r\nset FOO_TEST_2=%1");

    Map<String, String> result = new EnvReader().readBatEnv(file.toPath(), Collections.singletonList("arg_value"));
    assertEquals("123", result.get("FOO_TEST_1"));
    assertEquals("arg_value", result.get("FOO_TEST_2"));
  }

  @Test(timeout = 30000)
  public void loadingPs1Env() throws Exception {
    assumeWindows();

    File file = FileUtil.createTempFile("test file with spaces", ".ps1", true);
    FileUtil.writeToFile(file, "$env:FOO_TEST_1=\"123\"\r\n$env:FOO_TEST_2=$($args[0])");

    Map<String, String> result = new EnvReader().readPs1Env(file.toPath(), Collections.singletonList("arg_value"));
    assertEquals("123", result.get("FOO_TEST_1"));
    assertEquals("arg_value", result.get("FOO_TEST_2"));
  }

  @Test(timeout = 30000)
  public void loadingBatEnv_ErrorHandling() throws Exception {
    assumeWindows();

    File file = FileUtil.createTempFile("test", ".bat", true);
    FileUtil.writeToFile(file, "echo some error\r\nexit /B 1");

    try {
      new EnvReader().readBatEnv(file.toPath(), Collections.emptyList());
      fail("error should be reported");
    }
    catch (Exception e) {
      String text = collectTextAndAttachment(e);
      assertTrue(text, text.contains("some error"));
    }
  }

  @Test(timeout = 30000)
  public void loadingPs1Env_ErrorHandling() throws Exception {
    assumeWindows();

    File file = FileUtil.createTempFile("test_failure", ".ps1", true);
    FileUtil.writeToFile(file, "echo \"some failure\"\r\nWrite-Error \"some error\"\r\nexit 100");

    try {
      new EnvReader().readPs1Env(file.toPath(), Collections.emptyList());
      fail("error should be reported");
    }
    catch (Exception e) {
      String errorText = collectTextAndAttachment(e);
      assertTrue(errorText, errorText.contains("some error"));
      assertTrue(errorText, errorText.contains("some failure"));
    }
  }

  private static @NotNull String collectTextAndAttachment(Exception e) {
    StringBuilder errorTextBuilder = new StringBuilder(e.getMessage());
    if (e instanceof ExceptionWithAttachments) {
      Arrays.stream(((ExceptionWithAttachments)e).getAttachments()).forEach(attachment -> errorTextBuilder.append(attachment.getDisplayText()));
    }
    String errorText = errorTextBuilder.toString();
    return errorText;
  }

  @Test
  public void testPath() {
    String escaped = File.pathSeparator.equals(";") ? "\\;" : File.pathSeparator;
    List<String> list = substitute("PATH=/foo/bar" + escaped + "$PATH$", "PATH=/hey");
    assertEquals("/foo/bar" + File.pathSeparator + "/hey", list.get(0));
  }

  @Test
  public void testParentFoo() {
    List<String> list = substitute("BAR=$FOO$", "FOO=/hey");
    assertEquals("/hey", list.get(0));
  }

  @Test
  public void testFooBar() {
    List<String> list = substitute("FOO=/hey;BAR=$FOO$", "");
    assertEquals("/hey", list.get(0));
    assertEquals("/hey", list.get(1));
  }

  @Test
  public void testTransitive() {
    List<String> list = substitute("FOO=/hey;BAR=$FOO$;THIRD=$BAR$", "");
    assertEquals("/hey", list.get(0));
    assertEquals("/hey", list.get(1));
    assertEquals("/hey", list.get(2));
  }

  private static List<String> substitute(String environment, String parent) {
    Map<String, String> env = EnvVariablesTable.parseEnvsFromText(environment);
    Map<String, String> parentEnv = EnvVariablesTable.parseEnvsFromText(parent);
    EnvironmentUtil.inlineParentOccurrences(env, parentEnv);
    return env.values().stream().toList();
  }
}
