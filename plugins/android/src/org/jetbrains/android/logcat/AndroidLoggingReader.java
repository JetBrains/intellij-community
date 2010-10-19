package org.jetbrains.android.logcat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;

/**
* @author Eugene.Kudelevsky
*/
public abstract class AndroidLoggingReader extends Reader {
  @NotNull
  protected abstract Object getLock();

  @Nullable
  protected abstract Reader getReader();

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    synchronized (getLock()) {
      Reader reader = getReader();
      return reader != null ? reader.read(cbuf, off, len) : -1;
    }
  }

  @Override
  public boolean ready() throws IOException {
    Reader reader = getReader();
    return reader != null ? reader.ready() : false;
  }

  @Override
  public void close() throws IOException {
    Reader reader = getReader();
    if (reader != null) reader.close();
  }
}
