/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.io.fs;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;

class IoFile implements IFile {
  private final File myFile;
  private static final IFile[] EMPTY_ARRAY = new IFile[0];

  public IoFile(@NotNull final File file) {
    myFile = file;
  }

  public boolean exists() {
    return myFile.exists();
  }

  public byte[] loadBytes() throws IOException {
    return FileUtil.loadFileBytes(myFile);
  }

  public InputStream openInputStream() throws FileNotFoundException {
    return new FileInputStream(myFile);
  }

  public OutputStream openOutputStream() throws FileNotFoundException {
    return new FileOutputStream(myFile);
  }

  public boolean delete() {
    return myFile.delete();
  }

  public void renameTo(final IFile newFile) throws IOException {
    FileUtil.rename(myFile, ((IoFile)newFile).myFile);
  }

  public void createParentDirs() {
    FileUtil.createParentDirs(myFile);
  }

  public IFile getParentFile() {
    return new IoFile(myFile.getParentFile());
  }

  public String getName() {
    return myFile.getName();
  }

  public String getPath() {
    return myFile.getPath();
  }

  public String getCanonicalPath() {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return myFile.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
    }
    else {
      try {
        return myFile.getCanonicalPath();
      }
      catch (IOException e) {
        return null;
      }
    }
  }

  public String getAbsolutePath() {
    return myFile.getAbsolutePath();
  }

  public long length() {
    return myFile.length();
  }

  public IFile getChild(final String childName) {
    return new IoFile(new File(myFile, childName)); 
  }

  public boolean isDirectory() {
    return myFile.isDirectory();
  }

  public IFile[] listFiles() {
    final File[] files = myFile.listFiles();
    if (files == null) return EMPTY_ARRAY;

    IFile[] result = new IoFile[files.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = new IoFile(files[i]);
    }

    return result;
  }

  public void mkDir() {
    myFile.mkdir();
  }

  public long getTimeStamp() {
    return myFile.lastModified();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IoFile ioFile = (IoFile)o;

    if (!myFile.equals(ioFile.myFile)) return false;

    return true;
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public String toString() {
    return myFile.toString();
  }
}
