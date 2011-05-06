/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
class C {
  void m1(int i) {  // ok
    if (i == 0) System.out.println("zero"); else if (i == 1) System.out.println("one"); else System.out.println("many");
  }

  void m1(char c) {  // ok
    if (c == '0') System.out.println("zero"); else if (c == '1') System.out.println("one"); else System.out.println("many");
  }

  void m1(int i) {  // bad, long literals
    if (i == 0L) System.out.println("zero"); else if (i == 1L) System.out.println("one"); else System.out.println("many");
  }

  void m2(long l) {  // bad, long expression
    if (l == 0) System.out.println("zero"); else if (l == 1) System.out.println("one"); else System.out.println("many");
  }
}