// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    var lines = new String[]{"V1=single line", "V2=multiple\nlines", "V3=single line", "PWD=?", ""};
    var map = EnvironmentUtil.parseEnv(lines);
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
