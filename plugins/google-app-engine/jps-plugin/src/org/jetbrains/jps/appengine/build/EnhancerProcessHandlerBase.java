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
package org.jetbrains.jps.appengine.build;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author nik
 */
public abstract class EnhancerProcessHandlerBase extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.enhancement.EnhancerProcessHandler");
  private Map<Key, EnhancerOutputParser> myParsers =
    FactoryMap.createMap(key -> new EnhancerOutputParser(ProcessOutputTypes.STDERR.equals(key)));

  public EnhancerProcessHandlerBase(Process process, @NotNull String commandLine, Charset charset) {
    super(process, commandLine, charset);
    addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        final int exitCode = event.getExitCode();
        if (exitCode != 0) {
          reportError("Enhancement process terminated with exit code " + exitCode);
        }
      }
    });
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    super.notifyTextAvailable(text, outputType);
    myParsers.get(outputType).appendText(text);
  }

  protected abstract void reportInfo(String message);

  protected abstract void reportError(String message);

  private class EnhancerOutputParser {
    @NonNls private static final String PLEASE_SEE_THE_LOGS_PREFIX = "Please see the logs [";
    private StringBuilder myBuffer = new StringBuilder();
    private final boolean myErrorStream;

    public EnhancerOutputParser(boolean errorStream) {
      myErrorStream = errorStream;
    }


    public void appendText(String text) {
      myBuffer.append(text);
      int start = 0;
      while (true) {
        int lineEnd1 = myBuffer.indexOf("\n", start);
        int lineEnd2 = myBuffer.indexOf("\r", start);
        if (lineEnd1 == -1 && lineEnd2 == -1) break;

        int lineEnd = lineEnd1 == -1 ? lineEnd2 : lineEnd2 == -1 ? lineEnd1 : Math.min(lineEnd1, lineEnd2);
        parseLine(myBuffer.substring(start, lineEnd).trim());
        start = lineEnd + 1;
      }

      myBuffer.delete(0, start);
    }

    private void parseLine(String line) {
      LOG.debug(myErrorStream ? "[err] " + line : line);
      if (myErrorStream) {
        reportError(line);
        return;
      }

      if (line.startsWith("Encountered a problem: ")) {
        reportError(line);
      }
      else if (line.startsWith(PLEASE_SEE_THE_LOGS_PREFIX)) {
        if (!showLogFileContent(line)) {
          reportError(line);
        }
      }
      else if (line.startsWith("DataNucleus Enhancer completed")) {
        reportInfo(line);
      }
    }

    private boolean showLogFileContent(String line) {
      final int i = line.lastIndexOf(']');
      if (i != -1) {
        File logFile = new File(line.substring(PLEASE_SEE_THE_LOGS_PREFIX.length(), i));
        if (logFile.exists()) {
          try {
            reportError(FileUtil.loadFile(logFile));
            return true;
          }
          catch (IOException ignored) {
          }
        }
      }
      return false;
    }
  }
}
