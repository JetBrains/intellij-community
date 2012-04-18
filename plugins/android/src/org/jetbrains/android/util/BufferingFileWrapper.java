package org.jetbrains.android.util;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene.Kudelevsky
 */
public class BufferingFileWrapper implements IAbstractFile {
  private final File myFile;

  public BufferingFileWrapper(@NotNull File file) {
    myFile = file;
  }

  @Override
  public InputStream getContents() throws StreamException {
    // it's not very good idea to return unclosed InputStream and entrust its closing to library, so let's read whole file
    try {
      final byte[] content = readFile();
      return new ByteArrayInputStream(content);
    }
    catch (IOException e) {
      throw new StreamException(e);
    }
  }

  private byte[] readFile() throws IOException {
    DataInputStream is = new DataInputStream(new FileInputStream(myFile));
    try {
      byte[] data = new byte[(int)myFile.length()];
      //noinspection ResultOfMethodCallIgnored
      is.readFully(data);
      return data;
    }
    finally {
      is.close();
    }
  }

  @Override
  public void setContents(InputStream source) throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream getOutputStream() throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PreferredWriteMode getPreferredWriteMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getOsLocation() {
    return myFile.getAbsolutePath();
  }

  @Override
  public boolean exists() {
    return myFile.isFile();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final File parentFile = myFile.getParentFile();
    return parentFile != null ? new BufferingFolderWrapper(parentFile) : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }
}
