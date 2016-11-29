/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Irina.Chernushina
 * @since 2.05.2012
 */
public class TestClientRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.vcs.TestClientRunner");
  private final boolean myTraceClient;
  private final File myClientBinaryPath;
  private final Map<String, String> myClientEnvironment;

  public TestClientRunner(boolean traceClient, File clientBinaryPath, @Nullable Map<String, String> clientEnvironment) {
    myTraceClient = traceClient;
    myClientBinaryPath = clientBinaryPath;
    myClientEnvironment = clientEnvironment;
  }

  public ProcessOutput runClient(@NotNull String exeName,
                                 @Nullable String stdin,
                                 @Nullable final File workingDir,
                                 String... commandLine) throws IOException {
    final List<String> arguments = new ArrayList<>();

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
      LOG.info("*** running:\n" + arguments);
      if (StringUtil.isNotEmpty(stdin)) {
        LOG.info("*** stdin:\n" + stdin);
      }
    }

    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    if (workingDir != null) {
      builder.directory(workingDir);
    }
    if (myClientEnvironment != null) {
      builder.environment().putAll(myClientEnvironment);
    }
    final Process clientProcess = builder.start();

    if (stdin != null) {
      final OutputStream outputStream = clientProcess.getOutputStream();
      try {
        final byte[] bytes = stdin.getBytes();
        outputStream.write(bytes);
      }
      finally {
        outputStream.close();
      }
    }

    final CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset(), StringUtil.join(arguments, " "));
    final ProcessOutput result = handler.runProcess(100*1000, false);
    if (myTraceClient || result.isTimeout()) {
      LOG.debug("*** result: " + result.getExitCode());
      final String out = result.getStdout().trim();
      if (out.length() > 0) {
        LOG.debug("*** output:\n" + out);
      }
      final String err = result.getStderr().trim();
      if (err.length() > 0) {
        LOG.debug("*** error:\n" + err);
      }
    }

    if (result.isTimeout()) {
      String processList = LogUtil.getProcessList();
      handler.destroyProcess();
      throw new RuntimeException("Timeout waiting for VCS client to finish execution:\n" + processList);
    }

    return result;
  }
}
