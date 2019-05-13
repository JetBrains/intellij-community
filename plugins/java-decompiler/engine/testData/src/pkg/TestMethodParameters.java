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

public class TestMethodParameters {
  TestMethodParameters(@Deprecated int p01) { }
  void m1(@Deprecated int p02) { }
  static void m2(@Deprecated int p03) { }

  class C1 {
    C1(@Deprecated int p11) { }
    void m(@Deprecated int p12) { }
  }

  static class C2 {
    C2(@Deprecated int p21) { }
    void m1(@Deprecated int p22) { }
    static void m2(@Deprecated int p23) { }
  }

  void local() {
    class Local {
      Local(@Deprecated int p31) { }
      void m(@Deprecated int p32) { }
    }
  }
}