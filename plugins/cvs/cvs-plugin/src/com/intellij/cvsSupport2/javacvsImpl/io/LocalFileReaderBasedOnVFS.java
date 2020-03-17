/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.javacvsImpl.ProjectContentInfoProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.util.Collection;

/**
 * author: lesya
 */
public class LocalFileReaderBasedOnVFS implements ILocalFileReader {

  private static final Logger LOG = Logger.getInstance(LocalFileReaderBasedOnVFS.class);

  private final ILocalFileReader myLocalFileReader;
  private final ProjectContentInfoProvider myProjectContentInfoProvider;

  public LocalFileReaderBasedOnVFS(ISendTextFilePreprocessor sendTextFilePreprocessor,
                                    ProjectContentInfoProvider projectContentInfoProvider) {
    myLocalFileReader = new LocalFileReader(sendTextFilePreprocessor);
    myProjectContentInfoProvider = projectContentInfoProvider;
  }

  @Override
  public void transmitTextFile(FileObject fileObject,
                               IConnectionStreams connectionStreams,
                               ICvsFileSystem cvsFileSystem) throws IOException {
    myLocalFileReader.transmitTextFile(fileObject,
                                       connectionStreams,
                                       cvsFileSystem);
  }

  @Override
  public void transmitBinaryFile(FileObject fileObject,
                                 IConnectionStreams connectionStreams,
                                 ICvsFileSystem cvsFileSystem) throws IOException {
    myLocalFileReader.transmitBinaryFile(fileObject,
                                         connectionStreams,
                                         cvsFileSystem);
  }

  @Override
  public boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return getVirtualFile(fileObject, cvsFileSystem) != null;
  }

  private VirtualFile getVirtualFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return CvsVfsUtil.findFileByIoFile(cvsFileSystem.getLocalFileSystem().getFile(fileObject));
  }

  @Override
  public boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
    VirtualFile virtualFile = getVirtualFile(fileObject, cvsFileSystem);
    if (virtualFile == null) return false;
    return CvsVfsUtil.isWritable(virtualFile);
  }

  @Override
  public void listFilesAndDirectories(DirectoryObject directoryObject,
                                      Collection<String> fileNames,
                                      Collection<String> directoryNames,
                                      ICvsFileSystem cvsFileSystem) {
    VirtualFile virtualDirectory = getVirtualFile(directoryObject, cvsFileSystem);
    if (virtualDirectory == null) return;

    for (final VirtualFile fileOrDirectory : CvsVfsUtil.getChildrenOf(virtualDirectory)) {
      if (CvsUtil.CVS.equals(fileOrDirectory.getName())) continue;
      if (!myProjectContentInfoProvider.fileIsUnderProject(fileOrDirectory)) continue;
      final String name = fileOrDirectory.getName();
      if (fileOrDirectory.isDirectory()) {
        if (directoryNames != null) {
          directoryNames.add(name);
        }
      }
      else {
        if (fileNames != null) {
          LOG.assertTrue(!name.isEmpty());
          fileNames.add(name);
        }
      }
    }

  }
}
