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
package com.intellij.vcs.log.graph

object TestGraphs {

  /*
  1
  |
  2
  |
  3
  |
  4
   */
  val line = graph {
    1(2)
    2(3)
    3(4)
    4()
  }

  /*
  1
  |\
  2 |
  |/
  3
   */
  val smallMerge = graph {
    1(2, 3)
    2(3)
    3()
  }

  /*
  0
  |\
  1 2
  |\|\
  3 4 5
   */
  val downTree = graph {
    0(1, 2)
    1(3, 4)
    2(4, 5)
    3()
    4()
    5()
  }

  /*
  0 1 2
  \/\/
  3 4
  \/
  5
   */
  val upTree = graph {
    0(3)
    1(3, 4)
    2(4)
    3(5)
    4(5)
    5()
  }

  /*
  1
  |\
  2 |
  | 3
  4 |
  | 5
  |/
  6
   */
  val simpleMerge = graph {
    1(2, 3)
    2(4)
    3(5)
    4(6)
    5(6)
    6()
  }

  /*
  1
  |\
  2 \
  |  3
  4 | \
  | 5 6
  7 |/
  | 8
  |/
  9
   */
  val plainMerge = graph {
    1(2, 3)
    2(4)
    3(5, 6)
    4(7)
    5(8)
    6(8)
    7(9)
    8(9)
    9()
  }

  /*
  1
  |\
  2 \
  |  3
  4 | \
  | 5 6
  7 \/
  | /\
  |/ 8
  9  |
  | /
  10
   */
  val notPlainMerge = graph {
    1(2, 3)
    2(4)
    3(5, 6)
    4(7)
    5(8)
    6(9)
    7(9)
    8(10)
    9(10)
    10()
  }

  /*
  1
  | 2
  |/|
  3 4
  | |\
  5 |6
  |/ /
  7 /
  |/
  8
   */
  val autoMerge = graph {
    1(3)
    2(3, 4)
    3(5)
    4(7, 6)
    5(7)
    6(8)
    7(8)
    8()
  }

  /*
  0
  |\
  | 1
  2 |\
  | | 3
  | 4 |
  |/  5
  6  /|
  |\/ /
  |7 /
  \ /
   8
   */
  val twoInit = graph {
    0(1, 2)
    1(3, 4)
    2(6)
    3(5)
    4(6)
    5(7, 8)
    6(7, 8)
    7()
    8()
  }

  /*
  1
  | 2
  3 |
  | 4
  5 /
  |/
  6
   */
  val twoBranches = graph {
    1(3)
    2(4)
    3(5)
    4(6)
    5(6)
    6()
  }
}

