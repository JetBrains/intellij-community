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


public class TestClassVar {

  private boolean field_boolean = (Math.random() > 0);
  public int field_int = 0;

  public void testFieldSSAU() {

    for (int i = 0; i < 10; i++) {

      try {
        System.out.println();
      }
      finally {
        if (field_boolean) {
          System.out.println();
        }
      }
    }
  }

  public Long testFieldSSAU1() { // IDEA-127466
    return new Long(field_int++);
  }

  public void testComplexPropagation() {

    int a = 0;

    while (a < 10) {

      int b = a;

      for (; a < 10 && a == 0; a++) {
      }

      if (b != a) {
        System.out.println();
      }
    }
  }
}
