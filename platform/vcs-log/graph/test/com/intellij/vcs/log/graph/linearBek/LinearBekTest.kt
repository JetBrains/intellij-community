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
package com.intellij.vcs.log.graph.linearBek

import org.junit.Test
import com.intellij.vcs.log.graph.TestGraphBuilder

class LinearBekTest {
  fun runTest(beforeLinearBekBuilder: TestGraphBuilder.() -> Unit, afterLinearBekBuilder: TestGraphBuilder.() -> Unit) {
    assertEquals(afterLinearBekBuilder, runLinearBek(beforeLinearBekBuilder))
  }

  /*
  0         0
  | \       |
  | 1       1
  | 2       2
  | 3       3
  | |       :
  4 |  ->   4
  5 |       5
  6 |       6
  |/        |
  7         7
  */
  @Test fun simpleGraphTest() = runTest({
    0(4, 1)
    1(2)
    2(3)
    3(7)
    4(5)
    5(6)
    6(7)
    7()
  }, {
    0(1)
    1(2)
    2(3)
    3(4.dot)
    4(5)
    5(6)
    6(7)
    7()
  })

  /*
  0           0
  |\          |
  | 1         1
  | |         :
  2 |         2
  |/          |
  3    ->     3
  |\          |
  | 4         4
  | |         :
  5 |         5
  |/          |
  6           6
   */
  @Test fun twoSectionsGraphTest() = runTest({
    0(2, 1)
    1(3)
    2(3)
    3(5, 4)
    4(6)
    5(6)
    6()
  }, {
    0(1)
    1(2.dot)
    2(3)
    3(4)
    4(5.dot)
    5(6)
    6()
  })

  /*
  0              0
  |\             |
  | 1            1
  | |\           |
  | | 2          2
  | | |          :
  | 3 |   ->     3
  | |/           |
  | 4            4
  | |            :
  5 |            5
  |/             |
  6              6
   */
  @Test fun recursiveSectionsTest() = runTest({
    0(5, 1)
    1(3, 2)
    2(4)
    3(4)
    4(6)
    5(6)
    6()
  }, {
    0(1)
    1(2)
    2(3.dot)
    3(4)
    4(5.dot)
    5(6)
    6()
  })

  /*
  0             0
  |\            |
  | 1           1
  | |           :
  2 |           2
  |\|           |
  | 3     ->    3
  | |           :
  4 |           4
  |/            |
  5             5
   */
  @Test fun diagonalTest() = runTest({
    0(2, 1)
    1(3)
    2(4, 3)
    3(5)
    4(5)
    5()
  }, {
    0(1)
    1(2.dot)
    2(3)
    3(4.dot)
    4(5)
    5()
  })

  /*
  0             0
  |\            |
  | 1           1
  |/|    ->     :
  2 |           2
  \|            :
  3             3
   */
  @Test fun differentDiagonalTest() = runTest({
    0(2, 1)
    1(2, 3)
    2(3)
    3()
  }, {
    0(1)
    1(2.dot)
    2(3.dot)
    3()
  })

  /*
  0                0
  |\               |
  | 1              1
  | |\             |
  | 2 \            2
  | |\|            :
  | | 3            3
  | \|             :
  | 4              4
  | \      ->      :
  5  \             5
  |\  \            |
  6 \ |            6
  |\| |            :
  | 7 |            7
  |/  |            :
  8  /             8
  |/               |
  9                9
   */
  @Test fun complicatedBranchesTest() = runTest({
    0(5, 1)
    1(2, 3)
    2(4, 3)
    3(4)
    4(9)
    5(6, 7)
    6(8, 7)
    7(8)
    8(9)
    9()
  }, {
    0(1)
    1(2)
    2(3.dot)
    3(4.dot)
    4(5.dot)
    5(6)
    6(7.dot)
    7(8.dot)
    8(9)
    9()
  })

  /*
  0                0
  |\               |
  | 1              1
  | |\             |
  | | 2            2
  | | 3            3
  | |/|            :
  | 4 |    ->      4
  | 5 |            5
  | | |            :
  6 | |            6
  |/ /             |
  7 /              7
  |/               :
  8                8
   */
  @Test fun interestingTest() = runTest({
    0(6, 1)
    1(4, 2)
    2(3)
    3(4, 8)
    4(5)
    5(7)
    6(7)
    7(8)
    8()
  }, {
    0(1)
    1(2)
    2(3)
    3(4.dot)
    4(5)
    5(6.dot)
    6(7)
    7(8.dot)
    8()
  })

  /*
    0                0
    |\               |
    | 1              1
    | |\             |
    | | 2            2
    | | |\           |
    | | | 3          3
    | | | |          :
    | | 4 |          4
    | |/  |          :
    | 5   |    ->    5
    | 6   |          6
    | |  /           :
    7 | /            7
    |/ /             |
    8 /              8
    |/               |
    9                9
     */
  @Test fun interestingTest2() = runTest({
    0(7, 1)
    1(5, 2)
    2(4, 3)
    3(9)
    4(5)
    5(6)
    6(8)
    7(8)
    8(9)
    9()
  }, {
    0(1)
    1(2)
    2(3)
    3(4.dot)
    4(5.dot)
    5(6)
    6(7.dot)
    7(8)
    8(9)
    9()
  })

  /*
  0                0
  |\               |
  | 1              1
  | |\             |
  | | 2            2
  | | |            :
  3 | |    ->      3
  4 | |            4
  |/ /             |
  5 /              5
  \/               |
  6                6
   */
  @Test fun smallRecursiveBranchesTest() = runTest({
    0(3, 1)
    1(5, 2)
    2(6)
    3(4)
    4(5)
    5(6)
    6()
  }, {
    0(1)
    1(2)
    2(3.dot)
    3(4)
    4(5)
    5(6)
    6()
  })

  /*
  0                0
  |\               |
  | 1              1
  | 2              2
  | |\             |\
  | | 3            | 3
  | | 4            | 4
  | | |\           | 5
  | | | \          | :
  | | | 5    ->    6 :
  | 6 | |          7 :
  | 7 | |          :'
  8 |/  |          8
  |/|   |          |
  9 |  /           9
  |/  /            |
  10 /             10
  11               11
   */
  @Test fun twoTailsBranchTest() = runTest({
    0(8, 1)
    1(2)
    2(6, 3)
    3(4)
    4(9, 5)
    5(11)
    6(7)
    7(10)
    8(9)
    9(10)
    10(11)
    11()
  }, {
    0(1)
    1(2)
    2(6, 3)
    3(4)
    4(5)
    5(8.dot)
    6(7)
    7(8.dot)
    8(9)
    9(10)
    10(11)
    11()
  })

  /*
  0                0
  |\   1           |  1
  | 2 |            2 /
  | |/             |/
  | 3       ->     3
  | |              :
  4 |              4
  |/               |
  5                5
   */
  @Test fun incomingEdgeTest() = runTest({
    0(4, 2)
    1(3)
    2(3)
    3(5)
    4(5)
    5()
  }, {
    0(2)
    1(3)
    2(3)
    3(4.dot)
    4(5)
    5()
  })

  /*
  0                0
  |\               |
  | 1              1
  | |\             :
  2 ||             2
  |/ |             |
  3  |     ->      3
  |\ |             :
  | \|             :
  | 4              4
  | |              :
  5 |              5
  |/               |
  6                6
   */
  @Test fun hiddenIncomingEdges() = runTest({
    0(2, 1)
    1(3, 4)
    2(3)
    3(5, 4)
    4(6)
    5(6)
    6()
  }, {
    0(1)
    1(2.dot)
    2(3)
    3(4.dot)
    4(5.dot)
    5(6)
    6()
  })

  /*
  0                0
  |\               |
  | 1              1
  | |\             |
  | | 2            2
  | | 3            3
  | | 4            4
  | |/             :
  |/               :
  5                5
  6                6
  7                7
   */
  @Test fun testMergeWithOldCommit() = runTest({
    0(5, 1)
    1(5, 2)
    2(3)
    3(4)
    4(5)
    5(6)
    6(7)
    7()
  }, {
    0(1)
    1(2)
    2(3)
    3(4)
    4(5.dot)
    5(6)
    6(7)
    7()
  })

  /*
  0                0
  |   1            | 1
  2   |            2 |
  |\  |            | |
  | 3 |     ->     3 |
  | |/             |/
  | 4              4
  |/               :
  5                5
  */
  @Test fun testHeadsOrder1() = runTest({
    0(2)
    1(4)
    2(5, 3)
    3(4)
    4(5)
    5()
  }, {
    0(2)
    1(4)
    2(3)
    3(4)
    4(5.dot)
    5()
  })

  /*
       0               0
   1   |           1   |
   2   |           2   |
   |\  |           |   |
   | 3 |     ->    3  /
   | |/            |/
   | 4             4
   |/              :
   5               5
   */
  @Test fun testHeadsOrder2() = runTest({
    0(4)
    1(2)
    2(5, 3)
    3(4)
    4(5)
    5()
  }, {
    0(4)
    1(2)
    2(3)
    3(4)
    4(5.dot)
    5()
  })

  /*
  0                0
  |\               |
  | 1              1
  | 2      ->      2
  |/               :
  3                3
  */
  @Test fun testKatisha() = runTest({
    0(1, 3)
    1(2)
    2(3)
    3()
  }, {
    0(1)
    1(2)
    2(3.dot)
    3()
  })

  /*
  0                0
  |\               |\
  | 1      ->      | 1
  | 2              | 2
  3                3
   */
  @Test fun testInitialImport() = runTest({
    0(3, 1)
    1(2)
    2()
    3()
  }, {
    0(3, 1)
    1(2)
    2()
    3()
  })

  /*
    0              0
    |\             |
    | 1    ->      1
    |/             :
    2              2
     */
  @Test fun testTriangle() = runTest({
    0(2, 1)
    1(2)
    2()
  }, {
    0(1)
    1(2.dot)
    2()
  })

  /*
  0
  |\
  1 \
  2 \
  |\ \
  3 \|
  |\ 4
  | \|
  | 5
  |/
  6
   */
  @Test fun reversedParentsTest() = runTest({
    0(1, 4)
    1(2)
    2(3, 4)
    3(5, 6)
    4(5)
    5(6)
    6()
  }, {
    0(1)
    1(2)
    2(3)
    3(4.dot)
    4(5)
    5(6.dot)
    6()
  })

  /*
  0               0
  |\              |
  | 1             1
  | |\            |\
  | | 2           | 2
  | 3 |    -->    3 :
  | \'            : :
  |/|             :'
  4 |             4
  |/              |
  5               5
   */
  @Test fun crossedTailsTest() = runTest({
    0(4, 1)
    1(3, 2)
    2(4)
    3(5)
    4(5)
    5()
  }, {
    0(1)
    1(3, 2)
    2(4.dot)
    3(4.dot)
    4(5)
    5()
  })
}