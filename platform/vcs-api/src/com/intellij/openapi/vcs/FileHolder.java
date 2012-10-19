/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/30/11
 * Time: 12:36 PM
 */
public class FileHolder {
  private final File myIoFile;
  private final VirtualFile myFile;
  private Boolean myIsDir;

  public FileHolder(File ioFile) {
    myIoFile = ioFile;
    myFile = null;
  }

  public FileHolder(VirtualFile file) {
    myFile = file;
    myIoFile = null;
  }
  
  public String getPath() {
    return myFile != null ? myFile.getPath() : myIoFile.getPath();
  }

  public String getName() {
    return myFile != null ? myFile.getName() : myIoFile.getName();
  }

  public boolean isDirectory() {
    if (myIsDir != null) return myIsDir;
    return myFile != null ? myFile.isDirectory() : myIoFile.isDirectory();
  }

  public boolean exists() {
    return myFile != null ? myFile.exists() : myIoFile.exists();
  }

  public boolean isValid() {
    return myFile != null ? myFile.isValid() : true;
  }

  public File getIoFile() {
    return myIoFile;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public boolean isVirtual() {
    return myFile != null;
  }

  public void setIsDir(boolean isDir) {
    myIsDir = isDir;
  }

  @Override
  public String toString() {
    return "FileHolder{" +
           "myIoFile=" + myIoFile +
           ", myFile=" + myFile +
           ", myIsDir=" + myIsDir +
           '}';
  }
}
