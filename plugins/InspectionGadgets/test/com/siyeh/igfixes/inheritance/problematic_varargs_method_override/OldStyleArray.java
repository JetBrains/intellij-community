/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.igfixes.inheritance.problematic_varargs_method_override;

class One {

  public void m(String... ss) {}
}
class Two extends One {
  public void m<caret>(String ss[]) {}
}
class Three {
  public static void main(String... args) {
    new Two().m(new String[]{"1", "2", "3"});
  }
}