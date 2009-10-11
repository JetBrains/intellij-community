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

import java.util.List;

/**
 * @author max
 */
public class SmartListTest extends TestCase {
  public void testEmpty() {
    List<Integer> l = new SmartList<Integer>();
    assertEquals(0, l.size());
  }

  public void testOneElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    assertEquals(1, l.size());
    assertEquals(1, l.get(0).intValue());
  }

  public void testTwoElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    assertEquals(2, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
  }

  public void testThreeElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    l.add(new Integer(3));
    assertEquals(3, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
  }

  public void testFourElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    l.add(new Integer(3));
    l.add(new Integer(4));
    assertEquals(4, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
    assertEquals(4, l.get(3).intValue());
  }
}
