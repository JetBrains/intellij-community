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
package com.intellij.util.graph;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.Iterator;

/**
 *  @author dsl
 */
public class GraphTestUtil {
  static <E> void assertIteratorsEqual(Iterator<E> expected, Iterator<E> found) {
    for (; expected.hasNext(); ) {
      Assert.assertTrue(found.hasNext());
      Assert.assertEquals(expected.next(), found.next());
    }
    Assert.assertFalse(found.hasNext());
  }

  public static Iterator<TestNode> iteratorOfArray(TestNode[] array) {
    return Arrays.asList(array).iterator();
  }
}
