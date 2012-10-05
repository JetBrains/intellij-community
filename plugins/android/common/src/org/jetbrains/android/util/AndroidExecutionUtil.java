/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Abstract external tool for compiler.
 *
 * @author Alexey Efimov
 */
public final class AndroidExecutionUtil {

  private AndroidExecutionUtil() {
  }

  @NotNull
    public static Map<AndroidCompilerMessageKind, List<String>> doExecute(String... argv) throws IOException {
    return doExecute(argv, Collections.<String, String>emptyMap());
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> doExecute(String[] argv, Map<? extends String, ? extends String> enviroment)
    throws IOException {
    ProcessBuilder builder = new ProcessBuilder(argv);
    builder.environment().putAll(enviroment);
    ProcessResult result = readProcessOutput(builder.start());
    Map<AndroidCompilerMessageKind, List<String>> messages = result.getMessages();
    int code = result.getExitCode();
    List<String> errMessages = messages.get(AndroidCompilerMessageKind.ERROR);

    if (code != 0 && errMessages.isEmpty()) {
      throw new IOException("Command \"" + concat(argv) + "\" execution failed with exit code " + code);
    }
    else {
      if (code == 0) {
        messages.get(AndroidCompilerMessageKind.WARNING).addAll(errMessages);
        errMessages.clear();
      }
      return messages;
    }
  }

  private static String concat(String... strs) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = strs.length; i < n; i++) {
      builder.append(strs[i]);
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @NotNull
  private static ProcessResult readProcessOutput(Process process) throws IOException {
    final AndroidOSProcessHandler handler = new AndroidOSProcessHandler(process, "");
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return new ProcessResult(handler.getInfoMessages(), handler.getErrorMessages(), exitCode);
  }

  private static final class ProcessResult {
    private final int myExitCode;
    private final Map<AndroidCompilerMessageKind, List<String>> myMessages;

    public ProcessResult(List<String> information, List<String> error, int exitCode) {
      myExitCode = exitCode;
      myMessages = new HashMap<AndroidCompilerMessageKind, List<String>>(2);
      myMessages.put(AndroidCompilerMessageKind.INFORMATION, information);
      myMessages.put(AndroidCompilerMessageKind.ERROR, error);
      myMessages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
    }

    public Map<AndroidCompilerMessageKind, List<String>> getMessages() {
      return myMessages;
    }

    public int getExitCode() {
      return myExitCode;
    }
  }

  public static <T> void addMessages(@NotNull Map<T, List<String>> messages, @NotNull Map<T, List<String>> toAdd) {
    for (Map.Entry<T, List<String>> entry : toAdd.entrySet()) {
      List<String> list = messages.get(entry.getKey());
      if (list == null) {
        list = new ArrayList<String>();
        messages.put(entry.getKey(), list);
      }
      list.addAll(entry.getValue());
    }
  }
}
