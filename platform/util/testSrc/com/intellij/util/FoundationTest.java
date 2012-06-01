/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ui.mac.foundation.Foundation;
import junit.framework.TestCase;

public class FoundationTest extends TestCase {
  public void testStrings() throws Exception {
    if (!SystemInfo.isMac) return;
    assertEquals("Test", Foundation.toStringViaUTF8(Foundation.nsString("Test")));
  }

  public void testEncodings() throws Exception {
    if (!SystemInfo.isMac) return;

    assertEquals("utf-8", Foundation.getEncodingName(4));
    assertEquals(null, Foundation.getEncodingName(0));
    assertEquals(null, Foundation.getEncodingName(-1));

    assertEquals(4, Foundation.getEncodingCode("utf-8"));
    assertEquals(4, Foundation.getEncodingCode("UTF-8"));

    assertEquals(-1, Foundation.getEncodingCode(""));
    assertEquals(-1, Foundation.getEncodingCode("asdasd"));
    assertEquals(-1, Foundation.getEncodingCode(null));

    assertEquals("utf-16", Foundation.getEncodingName(10));
    assertEquals(10, Foundation.getEncodingCode("utf-16"));

    assertEquals("utf-16le", Foundation.getEncodingName(2483028224l));
    assertEquals(2483028224l, Foundation.getEncodingCode("utf-16le"));
    assertEquals("utf-16be", Foundation.getEncodingName(2415919360l));
    assertEquals(2415919360l, Foundation.getEncodingCode("utf-16be"));
  }
}
