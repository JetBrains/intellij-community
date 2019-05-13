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
package pkg;

import java.util.ArrayList;
import java.util.List;

public class TestClassTypes {

  public void testBoolean() {

    byte var7 = 0;
    long time = System.currentTimeMillis();

    if (time % 2 > 0) {
      var7 = 1;
    }
    else if (time % 3 > 0) {
      var7 = 2;
    }

    if (var7 == 1) {
      System.out.println();
    }
  }

  public boolean testBit(int var0) {
    return (var0 & 1) == 1;
  }

  public void testSwitchConsts(int a) {

    switch (a) {
      case 88:
        System.out.println("1");
        break;
      case 656:
        System.out.println("2");
        break;
      case 65201:
      case 65489:
        System.out.println("3");
    }
  }

  public void testAssignmentType(List list) {

    List a = list;

    if (a != null) {
      (a = new ArrayList(a)).add("23");
    }

    System.out.println(a.size());
  }
}
