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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.netbeans.lib.cvsclient.io.IStreamLogger;
import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * author: lesya
 */
@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class StreamLogger implements IStreamLogger {

  private int myCloseCount = 0;

  private static final OutputStream DUMMY_OUTPUT_STREAM = new OutputStream() {
    @Override
    public void write(int b) {}
  };

  private OutputStream myLogOutput;

  private static final long MAX_OUTPUT_SIZE = 1000000;
  @NonNls private static final String OUTPUT_FILE_NAME = "cvs.log";

  private static OutputStream createFileOutputStream(final File cvsOutputFile) {
    try {
      return new BufferedOutputStream(new FileOutputStream(cvsOutputFile, true));
    }
    catch (FileNotFoundException ignored) {
      return DUMMY_OUTPUT_STREAM;
    }
  }

  @Override
  public OutputStream createLoggingOutputStream(final OutputStream outputStream) {
    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        outputStream.write(b);
        getOutputLogStream().write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
        getOutputLogStream().write(b, off, len);
      }

      @Override
      public void flush() throws IOException {
        outputStream.flush();
        getOutputLogStream().flush();
      }

      @Override
      public void close() throws IOException {
        myCloseCount++;
        if (myCloseCount == 2) {
          myLogOutput.close();
        }
      }
    };
  }

  // todo!!!! in memory logging
  @Override
  public InputStream createLoggingInputStream(final InputStream inputStream) {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        final int result = inputStream.read();
        final OutputStream logStream = getInputLogStream();
        logStream.write(result);
        if (result == '\n') {
          logStream.flush();
        }
        return result;
      }

      @Override
      public void close() throws IOException {
        myCloseCount++;
        if (myCloseCount == 2 && myLogOutput != null) {
          myLogOutput.close();
          myLogOutput = null;
          myCloseCount = 0;
        }
      }

      // todo !!!! do not read byte by byte
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        final int read = read();
        if (read == -1) return -1;
        b[off] = (byte)read;
        return 1;
      }
    };
  }

  @Override
  public OutputStream getInputLogStream() {
    if (myLogOutput == null) {
      initLogOutput();
    }
    return myLogOutput;
  }

  private void initLogOutput() {
    if (CvsApplicationLevelConfiguration.getInstance().DO_OUTPUT) {
      final File cvsOutputFile = new File(PathManager.getLogPath(), OUTPUT_FILE_NAME);
      if (cvsOutputFile.isFile() && cvsOutputFile.length() > MAX_OUTPUT_SIZE) {
        FileUtil.delete(cvsOutputFile);
      }
      myLogOutput = createFileOutputStream(cvsOutputFile);
    } else {
      myLogOutput = DUMMY_OUTPUT_STREAM;
    }
  }

  @Override
  public OutputStream getOutputLogStream() {
    if (myLogOutput == null) {
      initLogOutput();
    }
    return myLogOutput;
  }
}
