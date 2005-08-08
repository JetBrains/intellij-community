package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.util.io.FileUtil;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.*;

/**
 * author: lesya
 */
public class StreamLogger implements IStreamLogger {

  private int myCloseCount = 0;

  private static final OutputStream DUMMY_OUTPUT_STREAM = new OutputStream() {
    public void write(int b) {

    }
  };

  private OutputStream myLogOutput;

  private static final long MAX_OUTPUT_SIZE = 1000000;

  public StreamLogger() {
  }

  private OutputStream createFileOutputStream(final File cvsOutputFile) {
    try {
      return new FileOutputStream(cvsOutputFile, true);
    }
    catch (FileNotFoundException e) {
      return DUMMY_OUTPUT_STREAM;
    }
  }

  public OutputStream createLoggingOutputStream(final OutputStream outputStream) {
    return new OutputStream() {
      public void write(int b) throws IOException {
        outputStream.write(b);
        getOutputLogStream().write(b);
        getOutputLogStream().flush();
      }

      public void flush() throws IOException {
        outputStream.flush();
      }

      public void close() throws IOException {
        myCloseCount++;
        if (myCloseCount == 2) {
          myLogOutput.close();
        }
      }
    };
  }

  public InputStream createLoggingInputStream(final InputStream inputStream) {
    return new InputStream() {
      public int read() throws IOException {
        int result = inputStream.read();
        getInputLogStream().write(result);
        getInputLogStream().flush();
        return result;
      }

      public void close() throws IOException {
        myCloseCount++;
        if (myCloseCount == 2) {
          myLogOutput.close();
          myLogOutput = null;
          myCloseCount = 0;
        }
      }

      public int read(byte b[], int off, int len) throws IOException {
        if (len == 0) return 0;
        final int read = read();
        if (read == -1) return -1;
        b[off] = (byte)read;
        return 1;
      }
    };
  }

  public OutputStream getInputLogStream() {
    if (myLogOutput == null) {
      initLogOutput();
    }
    return myLogOutput;
  }

  private void initLogOutput() {
    if (CvsApplicationLevelConfiguration.getInstance().DO_OUTPUT) {
      File cvsOutputFile = new File("cvs.output");
      if (cvsOutputFile.isFile() && cvsOutputFile.length() > MAX_OUTPUT_SIZE) {
        FileUtil.delete(cvsOutputFile);
      }
      myLogOutput = createFileOutputStream(cvsOutputFile);
    } else {
      myLogOutput = DUMMY_OUTPUT_STREAM;
    }
  }

  public OutputStream getOutputLogStream() {
    if (myLogOutput == null) {
      initLogOutput();
    }
    return myLogOutput;
  }
}
