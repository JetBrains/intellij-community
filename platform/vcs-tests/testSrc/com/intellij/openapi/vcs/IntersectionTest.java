/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.checkin.StepIntersection;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * @author irengrig
 *         Date: 2/18/11
 *         Time: 9:25 AM
 */
public class IntersectionTest extends TestCase {
  public void testSimple() {
    final Data second = new Data("second", 20, 21);
    final Data third = new Data("third", 22, 30);
    final Data[] data = {new Data("first", 10,19), second, third};
    final Area[] areas = {new Area("Afirst", 1,1), new Area("Asecond", 2,2), new Area("Athird", 21,21)};
    final StepIntersection<Data, Area> intersection = createIntersection(areas);
    final List<Data> result = intersection.process(Arrays.asList(data));
    Assert.assertTrue(result.size() == 1);
    Assert.assertTrue(result.contains(second));
  }

  public void testAllBefore() {
    final Data second = new Data("second", 20, 21);
    final Data third = new Data("third", 22, 30);
    final Data[] data = {new Data("first", 10,19), second, third};
    final Area[] areas = {new Area("Afirst", 100,100), new Area("Asecond", 101,102), new Area("Athird", 210,210)};
    final StepIntersection<Data, Area> intersection = createIntersection(areas);
    final List<Data> result = intersection.process(Arrays.asList(data));
    Assert.assertTrue(result.size() == 0);
  }

  public void testAllAfter() {
    final Data second = new Data("second", 20, 21);
    final Data third = new Data("third", 22, 30);
    final Data[] data = {new Data("first", 10,19), second, third};
    final Area[] areas = {new Area("Afirst", 1,1), new Area("Asecond", 2,2), new Area("Athird", 3,3)};
    final StepIntersection<Data, Area> intersection = createIntersection(areas);
    final List<Data> result = intersection.process(Arrays.asList(data));
    Assert.assertTrue(result.size() == 0);
  }

  public void testChangeIterators() {
    final Data first = new Data("first", 10, 20);
    final Data fourth = new Data("fourth", 70, 80);
    final Data[] data = {first, new Data("second", 30,40), new Data("third", 50,60), fourth, new Data("fifth", 90,100)};
    final Area[] areas = {new Area("Afirst", 1,1), new Area("Asecond", 11,12), new Area("Athird", 21,21),
      new Area("Afourth", 41,41), new Area("Afifth", 61,61), new Area("Asixth", 71,71)};
    final StepIntersection<Data, Area> intersection = createIntersection(areas);
    final List<Data> result = intersection.process(Arrays.asList(data));
    Assert.assertTrue(result.size() == 2);
    Assert.assertTrue(result.contains(first));
    Assert.assertTrue(result.contains(fourth));
  }

  public void testAreasOneAfterAnother() {
    final Data first = new Data("first", 77, 87);
    final Data fourth = new Data("fourth", 140, 158);
    final Data third = new Data("third", 225, 238);
    final Data fifth = new Data("fifth", 449, 456);
    final Data[] data = {first, fourth, third, fifth};
    final Area[] areas = {new Area("Afirst", 0,204), new Area("Asecond", 205,238), new Area("Athird", 239,457)};
    final StepIntersection<Data, Area> intersection = createIntersection(areas);
    final List<Data> result = intersection.process(Arrays.asList(data));
    Assert.assertEquals(4, result.size());
    Assert.assertTrue(result.contains(third));
    Assert.assertTrue(result.contains(first));
    Assert.assertTrue(result.contains(fourth));
    Assert.assertTrue(result.contains(fifth));
  }

  private StepIntersection<Data, Area> createIntersection(Area[] areas) {
    return new StepIntersection<>(o -> o.getTextRange(), o -> o.getTextRange(), Arrays.asList(areas));
  }

  private static class Data {
    private final String myName;
    private final int myFirst;
    private final int mySecond;

    protected Data(String name, int first, int second) {
      myName = name;
      myFirst = first;
      mySecond = second;
    }

    public TextRange getTextRange() {
      return new TextRange(myFirst, mySecond);
    }
  }

  private static class Area extends Data{
    private Area(String name, int first, int second) {
      super(name, first, second);
    }
  }
}
