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

public class TestInnerLocalPkg {
  public static void testStaticMethod() {
    class Inner {
      final String x;
      public Inner(String x) {
        this.x = x;
      }
    }
    new Inner("test");
    new Inner1Static("test");
    new Inner1Static.Inner2Static("test");
  }

  public void testMethod() {
    class Inner {
      final String x;
      public Inner(String x) {
        this.x = x;
      }
    }
    new Inner("test");
    new Inner1Static("test");
    new Inner1("test");
    new Inner1Static.Inner2Static("test");
  }

  class Inner1 {
    final String x;
    public Inner1(String x) {
      this.x = x;
    }
  }

  static class Inner1Static {
    final String x;
    public Inner1Static(String x) {
      this.x = x;
    }

    public static class Inner2Static {
      final String x;
      public Inner2Static(String x) {
        this.x = x;
      }
    }
  }
}