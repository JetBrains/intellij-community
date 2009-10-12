/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.util;

import java.io.PrintStream;

public class Dump {

  public static void out(String text) {
    print(text, System.out);
  }

  public static void err(String text) {
    print(text, System.err);
  }

  private static void print(String text, PrintStream ps) {
    Exception e = new Exception();
    StackTraceElement[] element = e.getStackTrace();
    StackTraceElement dumper = element[2];
    ps.println(text + " at " + dumper.toString());
  }
}
