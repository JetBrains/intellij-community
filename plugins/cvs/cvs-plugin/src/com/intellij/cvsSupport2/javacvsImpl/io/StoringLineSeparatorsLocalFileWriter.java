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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.application.options.CodeStyle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorProcessor;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.javacvsImpl.ProjectContentInfoProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.file.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * author: lesya
 */
public class StoringLineSeparatorsLocalFileWriter implements ILocalFileWriter {
  private final LocalFileWriter myLocalFileWriter;
  private final ErrorProcessor myErrorProcessor;
  private final UpdatedFilesManager myUpdatedFilesManager;
  private final String myCvsRoot;
  private final ReceiveTextFilePreprocessor myReceiveTextFilePreprocessor;
  private final ProjectContentInfoProvider myProjectContentInfoProvider;
  @NonNls private static final String COULD_NOT_DELETE_FILE_PREFIX = "Could not delete file ";

  public StoringLineSeparatorsLocalFileWriter(ReceiveTextFilePreprocessor preprocessor,
                                              ErrorProcessor errorProcessor,
                                              UpdatedFilesManager nonUpdateableFilesProcessor, String cvsRoot,
                                              ProjectContentInfoProvider projectContentInfoProvider) {
    myCvsRoot = cvsRoot;
    myLocalFileWriter = new LocalFileWriter(preprocessor);
    myErrorProcessor = errorProcessor;
    myUpdatedFilesManager = nonUpdateableFilesProcessor;
    myReceiveTextFilePreprocessor = preprocessor;
    myProjectContentInfoProvider = projectContentInfoProvider;
  }

  public void setNextFileMode(String nextFileMode) {
    myLocalFileWriter.setNextFileMode(nextFileMode);
  }

  public void setNextFileDate(Date modifiedDate) {
    myLocalFileWriter.setNextFileDate(modifiedDate);
  }

  public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName)
    throws IOException {
    final File originalFile = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
    final File targetFile = new File(originalFile.getParentFile(), newFileName);
    try {

      FileUtil.copy(originalFile, targetFile);
    }
    catch (IOException ex) {
      processException(ex, null, -1, cvsFileSystem.getLocalFileSystem(), fileObject, myCvsRoot);
    }
  }

  public void removeLocalFile(FileObject fileObject,
                              ICvsFileSystem cvsFileSystem,
                              IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
    if (hasToBeSkipped(fileObject, cvsFileSystem.getLocalFileSystem())) return;
    try {
      myLocalFileWriter.removeLocalFile(fileObject, cvsFileSystem, fileReadOnlyHandler);
    }
    catch (IOException ex) {
      processException(ex, null, -1, cvsFileSystem.getLocalFileSystem(), fileObject, myCvsRoot);
    }

  }

  public void writeBinaryFile(FileObject fileObject,
                              int length,
                              InputStream inputStream,
                              boolean readOnly,
                              IFileReadOnlyHandler fileReadOnlyHandler,
                              ICvsFileSystem cvsFileSystem) throws IOException {
    if (hasToBeSkipped(fileObject, cvsFileSystem.getLocalFileSystem())) {
      CvsUtil.skip(inputStream, length);
      return;
    }
    try {
      myLocalFileWriter.writeBinaryFile(fileObject, length, inputStream, readOnly, fileReadOnlyHandler, cvsFileSystem);
    }
    catch (IOException ex) {
      processException(ex, inputStream, length, cvsFileSystem.getLocalFileSystem(), fileObject, myCvsRoot);
    }

  }

  private boolean hasToBeSkipped(AbstractFileObject fileObject, IFileSystem localFileSystem) {
    if (fileObject == null) return false;
    final File localFile = localFileSystem.getFile(fileObject);
    if (!localFile.exists()) {
      return hasToBeSkipped(fileObject.getParent(), localFileSystem);
    }
    return !myProjectContentInfoProvider.fileIsUnderProject(localFile);
  }

  public void writeTextFile(FileObject fileObject,
                            int length,
                            InputStream inputStream,
                            boolean readOnly,
                            IReaderFactory readerFactory,
                            IFileReadOnlyHandler fileReadOnlyHandler,
                            IFileSystem fileSystem, final Charset charSet) throws IOException {
    if (hasToBeSkipped(fileObject, fileSystem)) {
      CvsUtil.skip(inputStream, length);
      return;
    }
    try {
      storeLineSeparatorInTheVirtualFile(fileSystem, fileObject);
      myLocalFileWriter.writeTextFile(fileObject, length, inputStream, readOnly, readerFactory, fileReadOnlyHandler,
                                      fileSystem, charSet);
    }
    catch (IOException ex) {
      processException(ex, inputStream, length, fileSystem, fileObject, myCvsRoot);
    }

  }

  private void processException(IOException ex,
                                InputStream inputStream,
                                int length,
                                IFileSystem fileSystem,
                                FileObject fileObject, String cvsRoot) throws IOException {
    File file = fileSystem.getFile(fileObject);
    VcsException vcsEx = new CvsException(ex.getLocalizedMessage() + ": " + file.getAbsolutePath(), cvsRoot);
    try {
      if (ex.getLocalizedMessage().startsWith(COULD_NOT_DELETE_FILE_PREFIX)) {
        myUpdatedFilesManager.couldNotUpdateFile(file);
        vcsEx.setVirtualFile(CvsVfsUtil.findFileByIoFile(file));
      }
    }
    finally {
      CvsUtil.skip(inputStream, length);
      vcsEx.setIsWarning(true);
      myErrorProcessor.addError(vcsEx);
    }
  }

  private void storeLineSeparatorInTheVirtualFile(IFileSystem fileSystem, FileObject fileObject) {
    final VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(fileSystem.getFile(fileObject));
    if (virtualFile != null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        FileDocumentManager.getInstance().getDocument(virtualFile);
        myReceiveTextFilePreprocessor.saveLineSeparatorForFile(virtualFile, getLineSeparatorFor(virtualFile));
      });
    }
  }

  private static String getLineSeparatorFor(VirtualFile virtualFile) {
    try {
      return FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
    }
    catch (Exception ex) {
      return CodeStyle.getDefaultSettings().getLineSeparator();
    }

  }
}
