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
package org.jetbrains.android.util;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract external tool for compiler.
 *
 * @author Alexey Efimov
 */
public final class ExecutionUtil {

  private static final String IGNORING = "ignoring";
  private static final String SKIPPING = "skipping";

  private ExecutionUtil() {
  }

  // can't be invoked from dispatch thread
  @NotNull
  public static Map<CompilerMessageCategory, List<String>> execute(String... argv) throws IOException {
    assert !ApplicationManager.getApplication().isDispatchThread();
    ProcessBuilder builder = new ProcessBuilder(argv);
    ProcessResult result = readProcessOutput(builder.start());
    Map<CompilerMessageCategory, List<String>> messages = result.getMessages();
    int code = result.getExitCode();
    List<String> errMessages = messages.get(CompilerMessageCategory.ERROR);

    if (code != 0 && errMessages.isEmpty()) {
      throw new IOException(AndroidBundle.message("command.0.execution.failed.with.exit.code.1", concat(argv), code));
    }
    else {
      if (code == 0) {
        messages.get(CompilerMessageCategory.INFORMATION).addAll(errMessages);
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
    assert !ApplicationManager.getApplication().isDispatchThread();
    OSProcessHandler handler = new OSProcessHandler(process, "");
    final List<String> information = new ArrayList<String>();
    final List<String> error = new ArrayList<String>();
    handler.addProcessListener(new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        String output = event.getText();
        if (StringUtil.isEmptyOrSpaces(output)) {
          return;
        }
        String[] lines = output.split("[\\n\\r]+");
        for (String line : lines) {
          String l = line.toLowerCase();
          if (outputType == ProcessOutputTypes.STDOUT) {
            information.add(line);
          }
          else if (outputType == ProcessOutputTypes.STDERR) {
            if (l.contains(IGNORING) || l.contains(SKIPPING)) {
              information.add(line);
            }
            else {
              error.add(line);
            }
          }
        }
      }
    });
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return new ProcessResult(information, error, exitCode);
  }

  private static final class ProcessResult {
    private final int myExitCode;
    private final Map<CompilerMessageCategory, List<String>> myMessages;

    public ProcessResult(List<String> information, List<String> error, int exitCode) {
      myExitCode = exitCode;
      myMessages = new HashMap<CompilerMessageCategory, List<String>>(2);
      myMessages.put(CompilerMessageCategory.INFORMATION, information);
      myMessages.put(CompilerMessageCategory.ERROR, error);
    }

    public Map<CompilerMessageCategory, List<String>> getMessages() {
      return myMessages;
    }

    public int getExitCode() {
      return myExitCode;
    }
  }
}
