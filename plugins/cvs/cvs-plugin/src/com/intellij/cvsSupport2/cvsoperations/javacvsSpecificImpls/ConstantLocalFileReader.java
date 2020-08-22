// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.file.*;

import java.util.Collection;

/**
 * author: lesya
 */
public final class ConstantLocalFileReader implements ILocalFileReader{

  private final boolean myFileExists;

  public static final ConstantLocalFileReader FOR_EXISTING_FILE = new ConstantLocalFileReader(true);

  private ConstantLocalFileReader(boolean fileExists) {
    myFileExists = fileExists;
  }

  @Override
  public boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return myFileExists;
  }

  @Override
  public void transmitBinaryFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void transmitTextFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void listFilesAndDirectories(DirectoryObject directoryObject, Collection<String> fileNames, Collection<String> directoryNames, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }
}
