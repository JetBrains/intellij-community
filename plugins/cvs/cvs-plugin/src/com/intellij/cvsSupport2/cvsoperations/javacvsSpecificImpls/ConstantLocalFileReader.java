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
package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.file.*;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import java.io.IOException;
import java.util.Collection;

/**
 * author: lesya
 */
public class ConstantLocalFileReader implements ILocalFileReader{

  private final boolean myFileExists;

  public static final ConstantLocalFileReader FOR_EXISTING_FILE = new ConstantLocalFileReader(true);

  private ConstantLocalFileReader(boolean fileExists) {
    myFileExists = fileExists;
  }

  public boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return myFileExists;
  }

  public void transmitBinaryFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) {
  }

  public void transmitTextFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) {
  }

  public void listFilesAndDirectories(DirectoryObject directoryObject, Collection<String> fileNames, Collection<String> directoryNames, ICvsFileSystem cvsFileSystem) {
  }

  public boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }
}
