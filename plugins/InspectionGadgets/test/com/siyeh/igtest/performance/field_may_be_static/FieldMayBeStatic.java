/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.igtest.performance.field_may_be_static;

public class FieldMayBeStatic
{
    private final int <warning descr="Field 'm_fooBar' may be 'static'">m_fooBar</warning> = 3;
    private final int m_fooBaz = m_fooBar;

    {
        System.out.println("m_fooBaz = " + m_fooBaz);
    }

    private static class Namer {
        private String name = "name";

        public String getString() {
            return name;
        }

        public void run() {

            new Namer() {
                final String <warning descr="Field 'constant' may be 'static'">constant</warning> = "";
                final String usage = "Usage: " + getString();
                public void action() {

                    System.out.println(usage);
                }
            };
        }
    }
}

class Outer {
  private final boolean value = isValue();

  private boolean isValue() {
    return true;
  }

  class Inner {
    private final boolean value = boolean.class != null;
  }

  void f(final boolean param) {
    new Runnable() {
      private final boolean value = param;
      public void run() {

      }
    }.run();
  }

}