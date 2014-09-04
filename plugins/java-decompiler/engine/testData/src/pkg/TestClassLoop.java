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

public class TestClassLoop {

  public static void testSimpleInfinite() {

    while (true) {
      System.out.println();
    }
  }

  public static void testFinally() {

    boolean a = (Math.random() > 0);

    while (true) {
      try {
        if (!a) {
          return;
        }
      }
      finally {
        System.out.println("1");
      }
    }
  }

  public static void testFinallyContinue() {

    boolean a = (Math.random() > 0);

    for (; ; ) {
      try {
        System.out.println("1");
      }
      finally {
        if (a) {
          System.out.println("3");
          continue;
        }
      }

      System.out.println("4");
    }
  }
}
