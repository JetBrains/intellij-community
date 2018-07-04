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
package decompiler;

// compile with java 8: javap -parameters TestMethodParametersAttr.java
public class TestMethodParametersAttr {
  TestMethodParametersAttr(int p01) { }
  void m1(int p02) { }
  static void m2(int p03) { }

  class C1 {
    C1(int p11) { }
    void m(int p12) { }
  }

  static class C2 {
    C2(int p21) { }
    void m1(int p22) { }
    static void m2(int p23) { }
  }

  void local() {
    class Local {
      Local(int p31) { }
      void m(int p32) { }
    }
  }

  interface I1 {
      void m1(int p41);
      void m2(final int p42);
  }

  abstract class C3 {
    abstract void m1(int p51);
    abstract void m2(final int p52);
  }

  static abstract class C4 {
    abstract void m1(int p51);
    abstract void m2(final int p52);
  }
}