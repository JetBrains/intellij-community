/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

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
}
