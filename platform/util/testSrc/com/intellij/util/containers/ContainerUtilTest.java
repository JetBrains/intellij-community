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

import com.intellij.openapi.util.Condition;

import java.util.*;

public class ContainerUtilTest extends junit.framework.TestCase {
  public void testFindInstanceOf() {
    Iterator<Object> iterator = Arrays.<Object>asList(new Integer(1), new ArrayList(), "1").iterator();
    String string = (String)ContainerUtil.find(iterator, FilteringIterator.instanceOf(String.class));
    junit.framework.Assert.assertEquals("1", string);
  }

  public void testConcatMulti() {
    List<Integer> l = ContainerUtil.concat(Arrays.asList(1, 2), Collections.EMPTY_LIST, Arrays.asList(3, 4));
    assertEquals(4, l.size());
    assertEquals(1, (int)l.get(0));
    assertEquals(2, (int)l.get(1));
    assertEquals(3, (int)l.get(2));
    assertEquals(4, (int)l.get(3));

    try {
      l.get(-1);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }

    try {
      l.get(4);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }
  }

  public void testIterateWithCondition() throws Exception {
    Condition<Integer> cond = new Condition<Integer>() {
      public boolean value(Integer integer) {
        return integer > 2;
      }
    };

    asserIterating(Arrays.asList(1, 4, 2, 5), cond, 4, 5);
    asserIterating(Arrays.asList(1, 2), cond);
    asserIterating(Collections.<Integer>emptyList(), cond);
    asserIterating(Arrays.asList(4), cond, 4);
  }

  private static void asserIterating(List<Integer> collection, Condition<Integer> condition, Integer... expected) {
    Iterable<Integer> it = ContainerUtil.iterate(collection, condition);
    List<Integer> actual = new ArrayList<Integer>();
    for (Integer each : it) {
      actual.add(each);
    }
    assertEquals(Arrays.asList(expected), actual);
  }

  public void testIteratingBackward() throws Exception {
    List<String> ss = new ArrayList<String>();
    ss.add("a");
    ss.add("b");
    ss.add("c");

    String log = "";
    for (String s : ss) {
      log += s;
    }

    for (String s : ContainerUtil.iterateBackward(ss)) {
      log += s;
    }

    assertEquals("abccba", log);
  }
}
