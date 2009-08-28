/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;

public class SafeFileOutputStream extends OutputStream {
  private final File myTargetFile;
  private final OutputStream myBackDoorStream;
  private boolean failed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    myTargetFile = target;
    myBackDoorStream = new FileOutputStream(backdoorFile());
  }

  private File backdoorFile() {
    return new File(myTargetFile.getParentFile(), myTargetFile.getName() + "___jb_bak___");
  }

  @Override
  public void write(byte[] b) throws IOException {
    try {
      myBackDoorStream.write(b);
    }
    catch (IOException e) {
      failed = true;
      throw e;
    }
  }

  public void write(int b) throws IOException {
    try {
      myBackDoorStream.write(b);
    }
    catch (IOException e) {
      failed = true;
      throw e;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      myBackDoorStream.write(b, off, len);
    }
    catch (IOException e) {
      failed = true;
      throw e;
    }
  }

  @Override
  public void flush() throws IOException {
    try {
      myBackDoorStream.flush();
    }
    catch (IOException e) {
      failed = true;
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      myBackDoorStream.close();
    }
    catch (IOException e) {
      FileUtil.delete(backdoorFile());
      throw e;
    }

    if (failed || !FileUtil.delete(myTargetFile)) {
      throw new IOException("Failed to save to " + myTargetFile + ". No data there harmed. Attempt result left at " + backdoorFile());
    }

    FileUtil.rename(backdoorFile(), myTargetFile);
  }

}
