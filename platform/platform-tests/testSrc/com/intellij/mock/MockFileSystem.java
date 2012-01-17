package com.intellij.mock;

import com.intellij.util.io.fs.IFile;
import com.intellij.util.io.fs.IFileSystem;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author mike
 */
public class MockFileSystem implements IFileSystem {
  private final Set<String> myExistingFiles = new HashSet<String>();
  private final Map<String, String> myContents = new HashMap<String, String>();

  @Override
  public IFile createFile(String filePath) {
    return new MockFile(filePath);
  }

  @Override
  public char getSeparatorChar() {
    throw new UnsupportedOperationException("Method getSeparatorChar not implemented in " + getClass());
  }

  public class MockFile implements IFile {
    private String myPath;

    public MockFile(final String path) {
      myPath = path;
    }

    public MockFile(final String path, final String content) {
      this(path);
      myContents.put(path, content);
    }

    @Override
    public byte[] loadBytes() throws IOException {
      throw new UnsupportedOperationException("Method loadBytes not implemented in " + getClass());
    }

    @Override
    public InputStream openInputStream() throws FileNotFoundException {
      assert exists() : "doesn't exist:" + myPath;
      assert myContents.containsKey(myPath) : "content not found:" + myPath;
      return new ByteArrayInputStream(myContents.get(myPath).getBytes());
    }

    @Override
    public OutputStream openOutputStream() throws FileNotFoundException {
      throw new UnsupportedOperationException("Method openOutputStream not implemented in " + getClass());
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException("Method delete not implemented in " + getClass());
    }

    @Override
    public void renameTo(final IFile newFile) throws IOException {
      throw new UnsupportedOperationException("Method renameTo not implemented in " + getClass());
    }

    @Override
    public void createParentDirs() {
    }

    @Override
    public IFile getParentFile() {
      String parentPath = myPath.substring(0, myPath.lastIndexOf("/"));
      return MockFileSystem.this.createFile(parentPath);
    }

    @Override
    public String getName() {
      throw new UnsupportedOperationException("Method getName not implemented in " + getClass());
    }

    @Override
    public String getPath() {
      return myPath;
    }

    @Override
    public String getCanonicalPath() {
      return getPath();
    }

    @Override
    public String getAbsolutePath() {
      return myPath;
    }

    @Override
    public long length() {
      assert myContents.containsKey(myPath);
      return myContents.get(myPath).getBytes().length;
    }

    @Override
    public IFile getChild(final String childName) {
      assert isDirectory();
      return new MockFile(myPath + "/" + childName);
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean exists() {
      return myExistingFiles.contains(myPath);
    }

    public String toString() {
      return "MockFile[" + myPath + "]";
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MockFile mockFile = (MockFile)o;

      return myPath.equals(mockFile.myPath);
    }

    public int hashCode() {
      return myPath.hashCode();
    }

    public MockFile createFile(final String name, final String content) {
      String path = myPath + "/" + name;
      myExistingFiles.add(path);
      return new MockFile(path, content);
    }


    @Override
    public IFile[] listFiles() {
      throw new UnsupportedOperationException("Method listFiles not implemented in " + getClass());
    }

    @Override
    public void mkDir() {
      throw new UnsupportedOperationException("Method mkDir not implemented in " + getClass());
    }

    @Override
    public long getTimeStamp() {
      return 0;
    }
  }
}
