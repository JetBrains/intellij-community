/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: sher
 * Date: 08.04.14
 * Time: 17:04
 */
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UrlUtilTest {
  @Test
  public void testJarUrlSplitter() {
    assertNull(URLUtil.splitJarUrl("/path/to/jar.jar/resource.xml"));
    assertNull(URLUtil.splitJarUrl("/path/to/jar.jar!resource.xml"));

    assertPair(URLUtil.splitJarUrl("/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("file:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");
    assertPair(URLUtil.splitJarUrl("file:///path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("jar:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("jar:file:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");
    assertPair(URLUtil.splitJarUrl("jar:file:///path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");
  }

  private static void assertPair(@Nullable Pair<String, String> pair, String expected1, String expected2) {
    assertNotNull(pair);
    assertEquals(expected1, pair.first);
    assertEquals(expected2, pair.second);
  }
}