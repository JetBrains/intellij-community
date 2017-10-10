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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.checkin.StepIntersection;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class IntersectionTest extends TestCase {
  public void testEmpty() {
    assertResult(
      createData(
      ),
      createArea(
        1, 1,  // 0
        2, 2,  // 1
        21, 21 // 2
      )
    );

    assertResult(
      createData(
        10, 19, // 0
        20, 21, // 1
        22, 30  // 2
      ),
      createArea(
      )
    );
  }

  public void testSimple() {
    assertResult(
      createData(
        10, 19, // 0
        20, 21, // 1
        22, 30  // 2
      ),
      createArea(
        1, 1,  // 0
        2, 2,  // 1
        21, 21 // 2
      ),
      1, 2
    );
  }

  public void testAllBefore() {
    assertResult(
      createData(
        10, 19, // 0
        20, 21, // 1
        22, 30  // 2
      ),
      createArea(
        100, 100, // 0
        101, 102, // 1
        210, 210  // 2
      )
    );
  }

  public void testAllAfter() {
    assertResult(
      createData(
        10, 19, // 0
        20, 21, // 1
        22, 30  // 2
      ),
      createArea(
        1, 1, // 0
        2, 2, // 1
        3, 3  // 2
      )
    );
  }

  public void testChangeIterators() {
    assertResult(
      createData(
        10, 20, // 0
        30, 40, // 1
        50, 60, // 2
        70, 80, // 3
        90, 100 // 4
      ),
      createArea(
        1, 1,   // 0
        11, 12, // 1
        21, 21, // 2
        41, 41, // 3
        61, 61, // 4
        71, 71  // 5
      ),
      0, 1,
      3, 5
    );
  }

  public void testAreasOneAfterAnother() {
    assertResult(
      createData(
        77, 87,   // 0
        140, 158, // 1
        225, 238, // 2
        449, 456  // 3
      ),
      createArea(
        0, 204,   // 0
        205, 238, // 1
        239, 457  // 2
      ),
      0, 0,
      1, 0,
      2, 1,
      3, 2
    );
  }

  public void testMultipleIntersections() {
    assertResult(
      createData(
        15, 100  // 0
      ),
      createArea(
        0, 10,   // 0
        12, 39,  // 1
        10, 20,  // 2
        25, 40,  // 3
        60, 105, // 4
        110, 120 // 5
      ),
      0, 1,
      0, 2,
      0, 3,
      0, 4
    );

    assertResult(
      createData(
        0, 10,   // 0
        12, 39,  // 1
        10, 20,  // 2
        25, 40,  // 3
        60, 105, // 4
        110, 120 // 5
      ),
      createArea(
        15, 100  // 0
      ),
      1, 0,
      2, 0,
      3, 0,
      4, 0
    );
  }

  public void testSuspiciousCase() {
    assertResult(
      createData(
        17, 34, // 0
        39, 41, // 1
        48, 51  // 2
      ),
      createArea(
        3, 9,   // 0
        10, 11, // 1
        13, 26, // 2
        32, 46, // 3
        66, 69  // 4
      ),
      0, 2,
      0, 3,
      1, 3
    );
  }


  private static List<Data> createData(int... values) {
    assert values.length % 2 == 0;

    List<Data> result = new ArrayList<>();
    for (int i = 0; i < values.length / 2; i++) {
      result.add(new Data(i, values[2 * i], values[2 * i + 1]));
    }
    return result;
  }

  private static List<Area> createArea(int... values) {
    assert values.length % 2 == 0;

    List<Area> result = new ArrayList<>();
    for (int i = 0; i < values.length / 2; i++) {
      result.add(new Area(i, values[2 * i], values[2 * i + 1]));
    }
    return result;
  }

  private static void assertResult(List<Data> datas, List<Area> areas, int... values) {
    List<Pair<Data, Area>> result = new ArrayList<>();
    StepIntersection.processIntersections(datas, areas, it -> it.getTextRange(), it -> it.getTextRange(),
                                          (data, area) -> result.add(Pair.create(data, area)));

    assert values.length % 2 == 0;
    assertEquals(values.length / 2, result.size());
    for (int i = 0; i < values.length / 2; i++) {
      Pair<Integer, Integer> expected = Pair.create(values[2 * i], values[2 * i + 1]);
      Pair<Integer, Integer> actual = Pair.create(result.get(i).first.index, result.get(i).second.index);
      assertEquals(expected, actual);
    }
  }

  private static class Data {
    private final int index;
    private final int first;
    private final int second;

    protected Data(int index, int first, int second) {
      this.index = index;
      this.first = first;
      this.second = second;
    }

    public TextRange getTextRange() {
      return new TextRange(first, second);
    }
  }

  private static class Area {
    private final int index;
    private final int first;
    private final int second;

    protected Area(int index, int first, int second) {
      this.index = index;
      this.first = first;
      this.second = second;
    }

    public TextRange getTextRange() {
      return new TextRange(first, second);
    }
  }
}
