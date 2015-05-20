/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
public class GetFileContentOperation extends LocalPathIndifferentOperation {

  @NonNls private static final String VERS_PREFIX = "VERS:";

  public static class FileContentReader {
    private ByteArrayOutputStream myContent = null;
    private byte[] myBinaryContent = null;
    @NonNls private static final String TEXT_MESSAGE_TAG = "text";
    private boolean myLastTagIsText = false;

    public boolean isEmpty() {
      return myContent == null && myBinaryContent == null;
    }

    public byte[] getReadContent() {
      if (myBinaryContent != null) {
        return myBinaryContent;
      } else {
        if (!myLastTagIsText && myContent.size() > 0) {
          myContent.write('\n');
        }
        return myContent.toByteArray();
      }
    }

    public void messageSent(final byte[] byteMessage, final boolean tagged) {
      if (myContent == null) myContent = new ByteArrayOutputStream();
      myLastTagIsText = false;
      if (tagged) {
        String tagType = readTagTypeFrom(byteMessage);
        if (tagType != null) {
          if (TEXT_MESSAGE_TAG.equals(tagType)) {
            final int textStartPosition = tagType.length();
            if (myContent.size() > 0) {
              myContent.write('\n');
            }
            myContent.write(byteMessage, textStartPosition + 1, byteMessage.length - textStartPosition - 1);
            myLastTagIsText = true;
          }
        }
      } else {
        if (myContent.size() > 0) {
          myContent.write('\n');
        }
        myContent.write(byteMessage, 0, byteMessage.length);
      }
    }

    private static String readTagTypeFrom(final byte[] byteMessage) {
      final StringBuilder result = new StringBuilder();
      for (byte b : byteMessage) {
        if (b == ' ') return result.toString();
        result.append((char)b);
      }
      return null;
    }

    public void binaryMessageSent(final byte[] bytes) {
      myBinaryContent = bytes;
    }
  }

  private static final byte NOT_LOADED = 0;
  private static final byte FILE_NOT_FOUND = 1;
  private static final byte DELETED = 2;
  private static final byte SUCCESSFULLY_LOADED = 3;
  private static final byte LOADING = 4;

  private byte myState = NOT_LOADED;

  private final FileContentReader myReader = new FileContentReader();

  private byte[] myFileBytes = null;
  private String myRevision;
  private final String myModuleName;
  private final CvsRootProvider myRoot;
  private CvsRevisionNumber myCvsRevisionNumber;
  private final RevisionOrDate myRevisionOrDate;

  public static GetFileContentOperation createForFile(VirtualFile file, RevisionOrDate revisionOrDate)
    throws CannotFindCvsRootException {
    File ioFile = CvsVfsUtil.getFileFor(file);
    return new GetFileContentOperation(new File(getPathInRepository(file)),
                                       CvsRootProvider.createOn(ioFile),
                                       revisionOrDate
    );
  }

  public static GetFileContentOperation createForFile(@NotNull VirtualFile file) throws CannotFindCvsRootException {
    return createForFile(file, RevisionOrDateImpl.createOn(file));
  }

  public static GetFileContentOperation createForFile(FilePath filePath) throws CannotFindCvsRootException {
    String pathInRepository = CvsEntriesManager.getInstance().getRepositoryFor(filePath.getVirtualFileParent()) + "/" + filePath.getName();
    return new GetFileContentOperation(new File(pathInRepository),
                                       CvsRootProvider.createOn(filePath.getIOFile()),
                                       RevisionOrDateImpl.createOn(filePath.getVirtualFileParent(), filePath.getName()));
  }

  public GetFileContentOperation(File cvsFile, CvsEnvironment environment, @NotNull RevisionOrDate revisionOrDate) {
    super(environment);
    myRevisionOrDate = revisionOrDate;
    myRoot = CvsRootProvider.createOn(null, environment);
    myModuleName = cvsFile.getPath().replace(File.separatorChar, '/');
    myCvsRevisionNumber = myRevisionOrDate.getCvsRevisionNumber();
  }

  private static String getPathInRepository(VirtualFile file) {
    return CvsUtil.getModuleName(file);
  }

  @SuppressWarnings({"RefusedBequest"})
  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(myRoot);
  }

  public CvsRootProvider getRoot() {
    return myRoot;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    myState = LOADING;
    myRoot.changeAdminRootTo(new File("."));
    myRoot.changeLocalRootTo(new File("."));
    CheckoutCommand command = new CheckoutCommand(null);
    command.setRecursive(false);
    command.addModule(myModuleName);
    command.setPrintToOutput(true);

    myRevisionOrDate.setForCommand(command);

    return command;
  }

  public String getRevision() {
    if (!isLoaded()) {
      return myRevisionOrDate.getRevision();
    }
    else {
      return myRevision;
    }

  }

  public synchronized byte[] getFileBytes() {
    if (myFileBytes == null) {
      if (myState == DELETED) return ArrayUtil.EMPTY_BYTE_ARRAY;
      myFileBytes = loadFileBytes();
    }
    return myFileBytes;
  }

  @Nullable
  public synchronized byte[] tryGetFileBytes() {
    if (myFileBytes == null && myState == LOADING) {
      myFileBytes = loadFileBytes();
    }
    return myFileBytes;
  }

  public boolean isDeleted() {
    if (myState == LOADING) {
      getFileBytes();
    }
    return myState == DELETED;
  }

  private synchronized byte[] loadFileBytes() {
    if (myState != LOADING) {
      LOG.error("state = " + String.valueOf(myState));
    }
    if (myReader.isEmpty()) {
      myState = DELETED;
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    else {
      myState = SUCCESSFULLY_LOADED;
      return myReader.getReadContent();
    }
  }

  public void gotEntry(FileObject abstractFileObject, Entry entry) {
    super.gotEntry(abstractFileObject, entry);
    if (entry == null) {
      myState = DELETED;
      myFileBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    else {
      myRevision = entry.getRevision();
      myCvsRevisionNumber = new CvsRevisionNumber(myRevision);
    }
  }

  public boolean fileNotFound() {
    tryGetFileBytes();
    return myState == FILE_NOT_FOUND;
  }

  public boolean isLoaded() {
    return myState != NOT_LOADED;
  }

  public CvsRevisionNumber getRevisionNumber() {
    LOG.assertTrue(myCvsRevisionNumber != null);
    return myCvsRevisionNumber;
  }

  protected String getOperationName() {
    return "checkout";
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    if (!error) {
      myReader.messageSent(byteMessage, tagged);
    } else if (message.startsWith(VERS_PREFIX)) {
      final String version = message.substring(5).trim();
      myRevision = version;
      myCvsRevisionNumber = new CvsRevisionNumber(version);
    }
  }

  public void binaryMessageSent(final byte[] bytes) {
    super.binaryMessageSent(bytes);
    myReader.binaryMessageSent(bytes);
  }

  @Override public boolean runInReadThread() {
    return false;
  }

  protected boolean runInExclusiveLock() {
    return false;
  }
}
