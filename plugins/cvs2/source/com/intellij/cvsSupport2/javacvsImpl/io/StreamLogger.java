package com.intellij.cvsSupport2.javacvsImpl.io;

import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * author: lesya
 */
public class StreamLogger implements IStreamLogger {


  private final boolean myOutoutToConsole = "true".equals(System.getProperty("cvs.print.output"));
  private static final OutputStream DUMMY_OUTPUT_STREAM = new OutputStream() {
    public void write(int b) {

    }
  };

  public StreamLogger() {

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
    };
  }

  public OutputStream getInputLogStream() {
    return myOutoutToConsole ? System.out : DUMMY_OUTPUT_STREAM;
  }

  public OutputStream getOutputLogStream() {
    return myOutoutToConsole ? System.err : DUMMY_OUTPUT_STREAM;
  }
}
