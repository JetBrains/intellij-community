// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author mike
 * @since Sep 19, 2002
 */
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
  public void load() {
    assumeTrue(SystemInfo.isUnix);
    Map<String, String> env = EnvironmentUtil.testLoader();
    assertTrue(env.size() >= System.getenv().size() / 2);
  }

  @Test(timeout = 30000)
  public void loadingBatEnv() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    File file = FileUtil.createTempFile("test", ".bat", true);
    FileUtil.writeToFile(file, "set FOO_TEST_1=123\r\nset FOO_TEST_2=%1");

    Map<String, String> result = new EnvironmentUtil.ShellEnvReader().readBatEnv(file, Arrays.asList("arg_value"));
    assertEquals("123", result.get("FOO_TEST_1"));
    assertEquals("arg_value", result.get("FOO_TEST_2"));
  }
  
  @Test(timeout = 30000)
  public void loadingBatEnv_ErrorHandling() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    File file = FileUtil.createTempFile("test", ".bat", true);
    FileUtil.writeToFile(file, "echo some error\r\nexit /B 1");

    try {
      new EnvironmentUtil.ShellEnvReader().readBatEnv(file, Arrays.asList());
      fail("error should be reported");
    }
    catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("some error"));
    }
  }
}