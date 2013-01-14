/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution;

import java.util.Map;

public class EnvPassingTest {
  public static void main(String[] args) {
    System.out.println("=====");
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      System.out.println(format(entry));
    }
    System.out.println("=====");
  }

  public static String format(Map.Entry<String, String> entry) {
    return entry.getKey() + "=" + entry.getValue().hashCode();
  }
}
