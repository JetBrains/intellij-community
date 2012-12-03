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

package com.intellij.util.containers;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * @author dyoma
 */
public class EnumeratorTest extends TestCase {
  public void test() {
    Enumerator enumerator = new Enumerator(10, ContainerUtil.canonicalStrategy());
    int[] indecies = enumerator.enumerate(new Object[]{"a", "b", "a"});
    assertTrue(Arrays.equals(new int[]{1, 2, 1}, indecies));
    indecies = enumerator.enumerate(new Object[]{"a", "c", "b"});
    assertTrue(Arrays.equals(new int[]{1, 3, 2}, indecies));
  }

  public void testWithShift() {
    Enumerator enumerator = new Enumerator(10, ContainerUtil.canonicalStrategy());
    int[] indecies = enumerator.enumerate(new Object[]{"1","a", "b", "a", "2"}, 1, 1);
    assertTrue(Arrays.equals(new int[]{1, 2, 1}, indecies));
  }
}
