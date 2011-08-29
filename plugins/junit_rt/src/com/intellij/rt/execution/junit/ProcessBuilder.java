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
package com.intellij.rt.execution.junit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clone of GeneralCommandLine.
 */
public class ProcessBuilder {
  public static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

  private final List myParameters = new ArrayList();

  public void add(final String parameter) {
    myParameters.add(parameter);
  }

  public void add(final List parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      add((String)parameters.get(i));
    }
  }

  public Process createProcess() throws IOException {
    if (myParameters.size() < 1) {
      throw new IllegalArgumentException("Executable name not specified");
    }

    final String[] command = new String[myParameters.size()];
    for (int i = 0; i < myParameters.size(); i++) {
      command[i] = prepareCommand(myParameters.get(i).toString());
    }

    return Runtime.getRuntime().exec(command);
  }

  // please keep in sync with GeneralCommandLine.prepareCommand()
  private static String prepareCommand(String parameter) {
    if (isWindows) {
      int pos = parameter.indexOf('\"');
      if (pos >= 0) {
        final StringBuffer buffer = new StringBuffer(parameter);
        do {
          buffer.insert(pos, '\\');
          pos += 2;
        }
        while ((pos = parameter.indexOf('\"', pos)) >= 0);
        parameter = buffer.toString();
      }
      else if (parameter.length() == 0) {
        parameter = "\"\"";
      }
    }
    return parameter;
  }
}
