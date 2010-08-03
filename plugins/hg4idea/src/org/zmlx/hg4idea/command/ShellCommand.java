// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;


final class ShellCommand {

  private static final Logger LOG = Logger.getInstance(ShellCommand.class.getName());

  private static final int BUFFER_SIZE = 1024;

  public HgCommandResult execute(List<String> commandLine, String dir, Charset charset) throws ShellCommandException, InterruptedException {
    if (commandLine == null || commandLine.isEmpty()) {
      throw new IllegalArgumentException("commandLine is empty");
    }
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
      if (dir != null) {
        processBuilder = processBuilder.directory(new File(dir));
      }
      Process process = processBuilder.start();
      Thread outReaderThread = startReader(
        new InputStreamReader(process.getInputStream(), charset), out
      );
      Thread errReaderThread = startReader(
        new InputStreamReader(process.getErrorStream()), err
      );
      process.waitFor();
      int exitValue = process.exitValue();
      outReaderThread.join();
      errReaderThread.join();
      return new HgCommandResult(out, err, exitValue );
    } catch (IOException e) {
      throw new ShellCommandException(e);
    }
  }

  private Thread startReader(final InputStreamReader in, final Writer writer) {
    Thread readingThread = new Thread(new Runnable() {
      public void run() {
        char[] buffer = new char[BUFFER_SIZE];
        int count;
        try {
          while ((count = in.read(buffer)) > 0) {
            writer.write(buffer, 0, count);
          }
          writer.flush();
        } catch (IOException e) {
          LOG.info(e.getMessage());
        } finally {
          try {
            in.close();
          }
          catch (IOException e) {
            // ignore
          }
        }
      }
    });
    readingThread.start();
    return readingThread;
  }

}
