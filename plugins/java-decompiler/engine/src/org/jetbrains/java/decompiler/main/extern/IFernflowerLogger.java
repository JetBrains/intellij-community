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
package org.jetbrains.java.decompiler.main.extern;

import java.util.HashMap;

public interface IFernflowerLogger {

  int TRACE = 1;
  int INFO = 2;
  int WARNING = 3;
  int ERROR = 4;
  int IMMEDIATE = 5;

  HashMap<String, Integer> mapLogLevel = new HashMap<String, Integer>() {{
    put("TRACE", 1);
    put("INFO", 2);
    put("WARN", 3);
    put("ERROR", 4);
    put("IMME", 5);
  }};

  String[] names = new String[]{""/*DUMMY ENTRY*/, "TRACE", "INFO", "WARNING", "ERROR", ""/*IMMEDIATE*/};

  void writeMessage(String message, int severity);

  void writeMessage(String message, Throwable t);

  void startClass(String classname);

  void endClass();

  void startWriteClass(String classname);

  void endWriteClass();

  void startMethod(String method);

  void endMethod();

  int getSeverity();

  void setSeverity(int severity);
}
