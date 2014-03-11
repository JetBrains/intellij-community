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

package com.intellij.vcs.log.newgraph.utils.impl;

import com.intellij.util.BooleanFunction;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TreeIntToIntMapTest {

  private static class Tester {

    public static Tester getTest(int longSize, String initVisibility) {
      Tester tester = new Tester(longSize, parseSet(initVisibility));

      assertEquals(initVisibility, tester.mapToString());
      return tester;
    }

    private static Set<Integer> parseSet(String visibility) {
      Set<Integer> visibleNodes = new HashSet<Integer>();
      if (visibility.length() == 0)
        return visibleNodes;

      for (String number : visibility.split("\\|")) {
        visibleNodes.add(Integer.decode(number));
      }
      return visibleNodes;
    }

    private final Set<Integer> myVisibleNodes;
    private final TreeIntToIntMap myTreeIntToIntMap;

    private Tester(int longSize, Set<Integer> visibleNodes) {
      myVisibleNodes = visibleNodes;
      myTreeIntToIntMap = TreeIntToIntMap.newInstance(new BooleanFunction<Integer>() {
        @Override
        public boolean fun(Integer integer) {
          return myVisibleNodes.contains(integer);
        }
      }, longSize);
    }

    public void changeVisibility(int updateFrom, int updateTo, String newVisibility) {
      myVisibleNodes.clear();
      myVisibleNodes.addAll(parseSet(newVisibility));

      myTreeIntToIntMap.update(updateFrom, updateTo);

      assertEquals(newVisibility, mapToString());
    }

    public String mapToString() {
      StringBuilder s = new StringBuilder();
      for (int shortIndex = 0; shortIndex < myTreeIntToIntMap.shortSize(); shortIndex++) {
        if (shortIndex != 0)
          s.append("|");

        s.append(myTreeIntToIntMap.getLongIndex(shortIndex));
      }
      return s.toString();
    }

    public String reverseMapToString() {
      StringBuilder s = new StringBuilder();
      for (int longIndex = 0; longIndex < myTreeIntToIntMap.longSize(); longIndex++) {
        if (longIndex != 0)
          s.append("|");

        s.append(myTreeIntToIntMap.getShortIndex(longIndex));
      }
      return s.toString();
    }


    public void testLongToShort(String expected) {
      assertEquals(expected, reverseMapToString());
    }
  }


  @Test
  public void simpleTest() {
    Tester tester = Tester.getTest(6, "0|1|2|3|4|5");

    tester.changeVisibility(1, 3, "0|2|4|5");
    tester.changeVisibility(2, 2, "0|4|5");
    tester.changeVisibility(1, 1, "0|1|4|5");
  }

  @Test
  public void testOneNode() {
    Tester tester = Tester.getTest(1, "0");

    tester.changeVisibility(0, 0, "");
    tester.changeVisibility(0, 0, "");
    tester.changeVisibility(0, 0, "0");
    tester.changeVisibility(0, 0, "0");
    tester.changeVisibility(0, 0, "");
  }

  @Test
  public void testTwoNodes() {
    Tester tester = Tester.getTest(2, "0");

    tester.changeVisibility(1, 1, "0|1");
    tester.changeVisibility(0, 1, "");
    tester.changeVisibility(0, 0, "0");
  }

  @Test
  public void test4Nodes() {
    Tester tester = Tester.getTest(4, "2|3");

    tester.changeVisibility(1, 3, "1");
    tester.changeVisibility(0, 2, "0|1|2");
    tester.changeVisibility(0, 0, "1|2");
  }

  @Test
  public void test5Nodes() {
    Tester tester = Tester.getTest(5, "0|1|2|3|4");

    tester.changeVisibility(4, 4, "0|1|2|3");
    tester.changeVisibility(3, 4, "0|1|2|4");
  }

  @Test
  public void testReverseMap() {
    Tester tester = Tester.getTest(7, "0|1|2|3|4|5|6");

    tester.testLongToShort("0|1|2|3|4|5|6");

    tester.changeVisibility(2, 4, "0|1|5|6");

    tester.testLongToShort("0|1|1|1|1|2|3");
  }

  @Test
  public void testReverseMap2() {
    Tester tester = Tester.getTest(8, "0|4|7");

    tester.testLongToShort("0|0|0|0|1|1|1|2");

    tester.changeVisibility(0, 0, "4|7");

    tester.testLongToShort("0|0|0|0|0|0|0|1");
  }

  @Test
  public void testReverseWithMinNodes() {
    Tester tester = Tester.getTest(1, "");

    tester.testLongToShort("0");

    tester.changeVisibility(0, 0, "0");

    tester.testLongToShort("0");
  }

  @Test
  public void testReverseWithMinNodes2() {
    Tester tester = Tester.getTest(2, "");

    tester.testLongToShort("0|0");

    tester.changeVisibility(1, 1, "1");

    tester.testLongToShort("0|0");

    tester.changeVisibility(0, 0, "0|1");

    tester.testLongToShort("0|1");
  }
}
