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
package com.intellij.rt.execution.junit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clone of GeneralCommandLine.
 */
public class ProcessBuilder {
  public static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

  private static final String WIN_SHELL_SPECIALS = "&<>()@^|";

  private final List myParameters = new ArrayList();
  private File myWorkingDir = null;

  public void add(final String parameter) {
    myParameters.add(parameter);
  }

  public void add(final List parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      add((String)parameters.get(i));
    }
  }

  public void setWorkingDir(File workingDir) {
    myWorkingDir = workingDir;
  }

  // please keep an implementation in sync with [util] CommandLineUtil.toCommandLine()
  public Process createProcess() throws IOException {
    if (myParameters.size() < 1) {
      throw new IllegalArgumentException("Executable name not specified");
    }

    String command = myParameters.get(0).toString();
    boolean winShell = isWindows &&
                       ("cmd".equalsIgnoreCase(command) || "cmd.exe".equalsIgnoreCase(command)) &&
                       myParameters.size() > 1 && "/c".equalsIgnoreCase(myParameters.get(0).toString());

    String[] commandLine = new String[myParameters.size()];
    commandLine[0] = command;

    for (int i = 1; i < myParameters.size(); i++) {
      String parameter = myParameters.get(i).toString();

      if (isWindows) {
        int pos = parameter.indexOf('\"');
        if (pos >= 0) {
          StringBuffer buffer = new StringBuffer(parameter);
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

        if (winShell && containsAnyChar(parameter, WIN_SHELL_SPECIALS)) {
          parameter = '"' + parameter + '"';
        }
      }

      commandLine[i] = parameter;
    }

    return Runtime.getRuntime().exec(commandLine, null, myWorkingDir);
  }

  private static boolean containsAnyChar(String value, String chars) {
    for (int i = 0; i < value.length(); i++) {
      if (chars.indexOf(value.charAt(i)) >= 0) {
        return true;
      }
    }
    return false;
  }
}
