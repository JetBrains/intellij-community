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

public class TestInnerSignature<A,B,C> {
  A a;
  B b;
  C c;

  public TestInnerSignature(A a,B b,C c) {
    this.a = a;
    this.b = b;
    this.c = c;
  }

  public class Inner {
    A a;
    B b;
    C c;

    public Inner(A a, B b, C c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }

  public static class InnerStatic<A,B,C> {
    A a;
    B b;
    C c;

    public InnerStatic(A a, B b, C c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }
}