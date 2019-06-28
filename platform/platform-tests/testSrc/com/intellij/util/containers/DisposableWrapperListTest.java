/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtilRt;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Test for {@link DisposableWrapperList}.
 */
public class DisposableWrapperListTest {
  @Test
  public void testCommonMethods() {
    DisposableWrapperList<String> l = new DisposableWrapperList<>();
    assertTrue(l.add("a"));
    assertTrue(l.add("b"));
    Disposable d1 = Disposer.newDisposable();
    assertNotNull(l.add("c", d1));
    Disposable d2 = Disposer.newDisposable();
    l.add(1, "d", d2);
    assertEquals(0, l.indexOf("a"));
    assertEquals(1, l.lastIndexOf("d"));
    assertEquals("b", l.get(2));
    assertEquals("c", l.get(3));
    assertEquals(4, l.size());
    assertEquals("[a, d, b, c]", Arrays.toString(ArrayUtilRt.toStringArray(l)));
    Disposer.dispose(d2);
    assertFalse(l.contains("d"));
    assertEquals(3, l.size());
    assertTrue(l.remove("a"));
    assertFalse(l.contains("a"));
    l.clear();
    assertTrue(l.isEmpty());
    Disposer.dispose(d1); // Cleanup after ourselves.
  }

  @Test
  public void testIterator() {
    DisposableWrapperList<String> l = new DisposableWrapperList<>();
    Disposable d = Disposer.newDisposable();
    l.add("a", d);
    l.add("b", d);
    l.add("c", d);
    l.add("d", d);
    StringBuilder buf = new StringBuilder();
    for (String s : l) {
      buf.append(s);
      if (s.equals("b")) {
        Disposer.dispose(d); // Remove all elements from the list but not from the iterator.
      }
    }
    assertEquals("abcd", buf.toString());
    assertTrue(l.isEmpty());
  }

  @Test
  public void testDuplicateElements() {
    DisposableWrapperList<String> l = new DisposableWrapperList<>();
    Disposable d1 = Disposer.newDisposable();
    assertNotNull(l.add("a", d1));
    assertNotNull(l.add("b", d1));
    Disposable d2 = Disposer.newDisposable();
    assertNotNull(l.add("a", d2));
    assertNotNull(l.add("b", d2));
    assertEquals(4, l.size());
    assertEquals(0, l.indexOf("a"));
    assertEquals(2, l.lastIndexOf("a"));
    assertTrue(l.remove("a"));
    assertEquals(3, l.size());
    assertTrue(l.contains("a"));
    Disposer.dispose(d2);
    assertEquals(1, l.size());
    Disposer.dispose(d1);
    assertTrue(l.isEmpty());
  }
}
