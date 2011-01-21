/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diagnostic;


import java.util.ArrayList;
import java.util.List;

/**
 * This is a very primitive fast logging class, primary for race-conditions debugging.
 */
public class Log {
  private static final List<String> myStrings = new ArrayList<String>();
  private static final List<Throwable> myThrowables = new ArrayList<Throwable>();
  private static final Object LOCK = new Object();

  private Log() { }

  public static void print(String text) {
    print(text, false);
  }

  public static void print(String text, boolean trace) {
    synchronized (LOCK) {
      myStrings.add(text);
      myThrowables.add(trace ? new Exception() : null);
    }
  }

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "UseOfSystemOutOrSystemErr"})
  public static void flush() {
    synchronized (LOCK) {
      for (int i = 0; i < myStrings.size(); i++) {
        String each = myStrings.get(i);
        Throwable trace = myThrowables.get(i);
        System.out.println(each);
        if (trace != null) {
          trace.printStackTrace(System.out);
        }
      }
      myStrings.clear();
      myThrowables.clear();
    }
  }
}
