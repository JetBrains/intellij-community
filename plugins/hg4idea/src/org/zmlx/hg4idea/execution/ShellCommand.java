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
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


public final class ShellCommand {

  private static final Logger LOG = Logger.getInstance(ShellCommand.class.getName());

  private static final int BUFFER_SIZE = 1024;
  private final boolean myRunViaBash;

  public ShellCommand(boolean runViaBash) {
    myRunViaBash = runViaBash;
  }

  public HgCommandResult execute(List<String> commandLine, String dir, Charset charset) throws ShellCommandException, InterruptedException {
    if (commandLine == null || commandLine.isEmpty()) {
      throw new IllegalArgumentException("commandLine is empty");
    }

    if (myRunViaBash) {
      // run via bash -cl <hg command> => need to escape bash special symbols
      // '-l' makes bash execute as a login shell thus reading .bash_profile
      String hgCommand = StringUtil.join(commandLine, " ");
      hgCommand = escapeBashControlCharacters(hgCommand);
      commandLine = new ArrayList<String>(3);
      commandLine.add("bash");
      commandLine.add("-cl");
      commandLine.add(hgCommand);
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

  /**
   * Escapes charactes in the command which will be executed via 'bash -c' - these are standard chars like \n, and some bash specials.
   * @param source Original string.
   * @return Escaped string.
   */
  private static String escapeBashControlCharacters(String source) {
    final String controlChars = "|>$\"'&";
    final String standardChars = "\b\t\n\f\r";
    final String standardCharsLetters = "btnfr";

    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (controlChars.indexOf(ch) > -1) {
        sb.append("\\").append(ch);
      } else {
        final int index = standardChars.indexOf(ch);
        if (index > -1) {
          sb.append("\\").append(standardCharsLetters.charAt(index));
        } else {
          sb.append(ch);
        }
      }
    }
    return sb.toString();
  }

}
