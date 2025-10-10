// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.util.system.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(30)
public class EnvironmentUtilTest {
  @Test void map() {
    assertNotNull(EnvironmentUtil.getEnvironmentMap());
  }

  @Test void path() {
    assertNotNull(EnvironmentUtil.getValue("PATH"));
    if (OS.CURRENT == OS.Windows) {
      assertNotNull(EnvironmentUtil.getValue("Path"));
    }
  }

  @Test void parse() {
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

  @Test void testPath() {
    var escaped = File.pathSeparator.equals(";") ? "\\;" : File.pathSeparator;
    var list = substitute("PATH=/foo/bar" + escaped + "$PATH$", "PATH=/hey");
    assertEquals("/foo/bar" + File.pathSeparator + "/hey", list.get(0));
  }

  @Test void testParentFoo() {
    var list = substitute("BAR=$FOO$", "FOO=/hey");
    assertEquals("/hey", list.get(0));
  }

  @Test void testFooBar() {
    var list = substitute("FOO=/hey;BAR=$FOO$", "");
    assertEquals("/hey", list.get(0));
    assertEquals("/hey", list.get(1));
  }

  @Test void testTransitive() {
    var list = substitute("FOO=/hey;BAR=$FOO$;THIRD=$BAR$", "");
    assertEquals("/hey", list.get(0));
    assertEquals("/hey", list.get(1));
    assertEquals("/hey", list.get(2));
  }

  @Test
  public void testWindowsCaseInsensitive() {
    assumeWindows();

    List<String> list = substitute("FIRST=$foo$;SECOND=$FOO$;THIRD=$fOo$", "FOo=/hey");
    assertEquals("/hey", list.get(0));
    assertEquals("/hey", list.get(1));
    assertEquals("/hey", list.get(2));
  }

  private static List<String> substitute(String environment, String parent) {
    var env = EnvVariablesTable.parseEnvsFromText(environment);
    var parentEnv = EnvVariablesTable.parseEnvsFromText(parent);
    EnvironmentUtil.inlineParentOccurrences(env, parentEnv);
    return env.values().stream().toList();
  }
}
