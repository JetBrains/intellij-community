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
package com.intellij.testFramework;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/2/12
 * Time: 4:51 PM
 */
public class TestClientRunner {
  protected boolean myTraceClient = false;
  protected File myClientBinaryPath;

  public TestClientRunner(boolean traceClient, File clientBinaryPath) {
    myTraceClient = traceClient;
    myClientBinaryPath = clientBinaryPath;
  }

  public ProcessOutput runClient(String exeName, @Nullable String stdin, @Nullable final File workingDir, String[] commandLine) throws
                                                                                                                                   IOException {
    final List<String> arguments = new ArrayList<String>();
    final File client = new File(myClientBinaryPath, SystemInfo.isWindows ? exeName + ".exe" : exeName);
    if (client.exists()) {
      arguments.add(client.toString());
    }
    else {
      // assume client is in path
      arguments.add(exeName);
    }
    Collections.addAll(arguments, commandLine);
    if (myTraceClient) {
      System.out.println("*** running:\n" + arguments);
      if (StringUtil.isNotEmpty(stdin)) {
        System.out.println("*** stdin:\n" + stdin);
      }
    }
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    if (workingDir != null) {
      builder.directory(workingDir);
    }
    Process clientProcess = builder.start();

    if (stdin != null) {
      OutputStream outputStream = clientProcess.getOutputStream();
      try {
        byte[] bytes = stdin.getBytes();
        outputStream.write(bytes);
      }
      finally {
        outputStream.close();
      }
    }

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(60*1000);
    if (myTraceClient || result.isTimeout()) {
      System.out.println("*** result: " + result.getExitCode());
      final String out = result.getStdout().trim();
      if (out.length() > 0) {
        System.out.println("*** output:\n" + out);
      }
      final String err = result.getStderr().trim();
      if (err.length() > 0) {
        System.out.println("*** error:\n" + err);
      }
    }
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for VCS client to finish execution");
    }
    return result;
  }
}
