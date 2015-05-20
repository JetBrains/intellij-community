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

import org.jetbrains.annotations.NonNls;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Deprecated
public interface IFile {
  boolean exists();

  byte[] loadBytes() throws IOException;

  InputStream openInputStream() throws IOException;
  OutputStream openOutputStream() throws FileNotFoundException;

  boolean delete();

  void renameTo(final IFile newFile) throws IOException;

  void createParentDirs();

  IFile getParentFile();

  String getName();

  String getPath();

  String getCanonicalPath();
  String getAbsolutePath();

  long length();

  IFile getChild(@NonNls final String childName);

  boolean isDirectory();

  IFile[] listFiles();

  void mkDir();

  long getTimeStamp();
}
