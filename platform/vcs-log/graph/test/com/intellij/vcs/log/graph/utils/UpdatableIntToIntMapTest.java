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

package com.intellij.vcs.log.graph.utils;

import com.intellij.util.BooleanFunction;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public abstract class UpdatableIntToIntMapTest {

  private static Set<Integer> parseSet(String visibility) {
    Set<Integer> visibleNodes = new HashSet<>();
    if (visibility.length() == 0) return visibleNodes;

    for (String number : visibility.split("\\|")) {
      visibleNodes.add(Integer.decode(number));
    }
    return visibleNodes;
  }


  private static class Tester {

    private final Set<Integer> myVisibleNodes;
    private final UpdatableIntToIntMap myUpdatableIntToIntMap;

    public Tester(UpdatableIntToIntMap updatableIntToIntMap, Set<Integer> visibleNodes) {
      myVisibleNodes = visibleNodes;
      myUpdatableIntToIntMap = updatableIntToIntMap;
    }

    public void changeVisibility(int updateFrom, int updateTo, String newVisibility) {
      myVisibleNodes.clear();
      myVisibleNodes.addAll(parseSet(newVisibility));

      myUpdatableIntToIntMap.update(updateFrom, updateTo);

      assertEquals(newVisibility, mapToString());
    }

    public String mapToString() {
      StringBuilder s = new StringBuilder();
      for (int shortIndex = 0; shortIndex < myUpdatableIntToIntMap.shortSize(); shortIndex++) {
        if (shortIndex != 0) s.append("|");

        s.append(myUpdatableIntToIntMap.getLongIndex(shortIndex));
      }
      return s.toString();
    }

    public String reverseMapToString() {
      StringBuilder s = new StringBuilder();
      for (int longIndex = 0; longIndex < myUpdatableIntToIntMap.longSize(); longIndex++) {
        if (longIndex != 0) s.append("|");

        s.append(myUpdatableIntToIntMap.getShortIndex(longIndex));
      }
      return s.toString();
    }


    public void testLongToShort(String expected) {
      assertEquals(expected, reverseMapToString());
    }
  }


  protected abstract UpdatableIntToIntMap createUpdatableIntToIntMap(@NotNull BooleanFunction<Integer> thisIsVisible, int longSize);

  public Tester getTest(int longSize, String initVisibility) {
    final Set<Integer> visibleNodes = parseSet(initVisibility);
    UpdatableIntToIntMap updatableIntToIntMap = createUpdatableIntToIntMap(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return visibleNodes.contains(integer);
      }
    }, longSize);
    Tester tester = new Tester(updatableIntToIntMap, visibleNodes);

    assertEquals(initVisibility, tester.mapToString());
    return tester;
  }

  @Test
  public void simpleTest() {
    Tester tester = getTest(6, "0|1|2|3|4|5");

    tester.changeVisibility(1, 3, "0|2|4|5");
    tester.changeVisibility(2, 2, "0|4|5");
    tester.changeVisibility(1, 1, "0|1|4|5");
  }

  @Test
  public void testOneNode() {
    Tester tester = getTest(1, "0");

    tester.changeVisibility(0, 0, "");
    tester.changeVisibility(0, 0, "");
    tester.changeVisibility(0, 0, "0");
    tester.changeVisibility(0, 0, "0");
    tester.changeVisibility(0, 0, "");
  }

  @Test
  public void testTwoNodes() {
    Tester tester = getTest(2, "0");

    tester.changeVisibility(1, 1, "0|1");
    tester.changeVisibility(0, 1, "");
    tester.changeVisibility(0, 0, "0");
  }

  @Test
  public void test4Nodes() {
    Tester tester = getTest(4, "2|3");

    tester.changeVisibility(1, 3, "1");
    tester.changeVisibility(0, 2, "0|1|2");
    tester.changeVisibility(0, 0, "1|2");
  }

  @Test
  public void test5Nodes() {
    Tester tester = getTest(5, "0|1|2|3|4");

    tester.changeVisibility(4, 4, "0|1|2|3");
    tester.changeVisibility(3, 4, "0|1|2|4");
  }

  @Test
  public void testReverseMap() {
    Tester tester = getTest(7, "0|1|2|3|4|5|6");

    tester.testLongToShort("0|1|2|3|4|5|6");

    tester.changeVisibility(2, 4, "0|1|5|6");

    tester.testLongToShort("0|1|1|1|1|2|3");
  }

  @Test
  public void testReverseMap2() {
    Tester tester = getTest(8, "0|4|7");

    tester.testLongToShort("0|0|0|0|1|1|1|2");

    tester.changeVisibility(0, 0, "4|7");

    tester.testLongToShort("0|0|0|0|0|0|0|1");
  }

  @Test
  public void testReverseWithMinNodes() {
    Tester tester = getTest(1, "");

    tester.testLongToShort("0");

    tester.changeVisibility(0, 0, "0");

    tester.testLongToShort("0");
  }

  @Test
  public void testReverseWithMinNodes2() {
    Tester tester = getTest(2, "");

    tester.testLongToShort("0|0");

    tester.changeVisibility(1, 1, "1");

    tester.testLongToShort("0|0");

    tester.changeVisibility(0, 0, "0|1");

    tester.testLongToShort("0|1");
  }

  @Test
  public void emptyTest() {
    Tester tester = getTest(0, "");
    tester.testLongToShort("");
  }

  @Test
  public void blockSizeTest() {
    getTest(0, "").testLongToShort("");
    getTest(1, "0").testLongToShort("0");
    getTest(2, "0|1").testLongToShort("0|1");
    getTest(3, "0|1|2").testLongToShort("0|1|2");
    getTest(4, "0|1|2|3").testLongToShort("0|1|2|3");
    getTest(5, "0|1|2|3|4").testLongToShort("0|1|2|3|4");
    getTest(6, "0|1|2|3|4|5").testLongToShort("0|1|2|3|4|5");
    getTest(7, "0|1|2|3|4|5|6").testLongToShort("0|1|2|3|4|5|6");
  }
}
