/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import junit.framework.TestCase;

public class UniqueFileNamesProviderTest extends TestCase{
  public void test1() throws Exception {
    UniqueFileNamesProvider p = new UniqueFileNamesProvider();
    assertEquals("aaa", p.suggestName("aaa"));
    assertEquals("bbb", p.suggestName("bbb"));
    assertEquals("Bbb2", p.suggestName("Bbb"));
    assertEquals("aaa3", p.suggestName("aaa"));
    assertEquals("aaa4", p.suggestName("aaa"));
    assertEquals("a_b_c", p.suggestName("a+b+c"));
    assertEquals("_", p.suggestName(null));
    assertEquals("_7", p.suggestName(""));
  }
}
