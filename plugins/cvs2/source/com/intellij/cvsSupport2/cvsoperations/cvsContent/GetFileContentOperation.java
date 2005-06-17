package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class GetFileContentOperation extends LocalPathIndifferentOperation {

  private final static byte NOT_LOADED = 0;
  private final static byte FILE_NOT_FOUND = 1;
  private final static byte DELETED = 2;
  private final static byte SUCCESSFULLY_LOADED = 3;
  private final static byte LOADING = 4;

  private byte myState = NOT_LOADED;

  private StringBuffer myContent = null;
  private byte[] myBinaryContent = null;

  private byte[] myFileBytes = null;
  private String myRevision;
  private String myModuleName;
  private CvsRootProvider myRoot;
  private CvsRevisionNumber myCvsRevisionNumber;
  private RevisionOrDate myRevisionOrDate;

  public static GetFileContentOperation createForFile(VirtualFile file, RevisionOrDate revisionOrDate)
    throws CannotFindCvsRootException {
    File ioFile = CvsVfsUtil.getFileFor(file);
    return new GetFileContentOperation(new File(getPathInRepository(ioFile)),
                                       CvsRootProvider.createOn(ioFile), revisionOrDate);
  }

  public static GetFileContentOperation createForFile(VirtualFile file) throws CannotFindCvsRootException {
    LOG.assertTrue(file != null);
    return createForFile(file, RevisionOrDateImpl.createOn(file));
  }

  public GetFileContentOperation(File cvsFile, CvsEnvironment environment, RevisionOrDate revisionOrDate) {
    super(environment);
    LOG.assertTrue(revisionOrDate != null);
    myRevisionOrDate = revisionOrDate;
    myRoot = CvsRootProvider.createOn(null, environment);
    myModuleName = cvsFile.getPath().replace(File.separatorChar, '/');
    myCvsRevisionNumber = myRevisionOrDate.getCvsRevisionNumber();
  }

  private static String getPathInRepository(File file) {
    return CvsUtil.getModuleName(file);
  }

  protected Collection getAllCvsRoots() {
    return Collections.singleton(myRoot);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    myState = LOADING;
    myRoot.changeAdminRootTo(new File("."));
    myRoot.changeLocalRootTo(new File("."));
    CheckoutCommand command = new CheckoutCommand();
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
    LOG.assertTrue(myState == LOADING, "state = " + String.valueOf(myState));
    if (myContent == null && myBinaryContent == null) {
      myState = DELETED;
      return null;
    }
    else {
      myState = SUCCESSFULLY_LOADED;
      return myBinaryContent != null ? myBinaryContent : myContent.toString().getBytes();
    }
  }

  public void gotEntry(FileObject abstractFileObject, Entry entry) {
    if (entry == null) {
      myState = DELETED;
      myFileBytes = new byte[0];
    }
    else {
      myRevision = entry.getRevision();
      myCvsRevisionNumber = new CvsRevisionNumber(myRevision);
    }
  }

  public boolean fileNotFound() {
    getFileBytes();
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

  public void messageSent(String message, boolean error, boolean tagged) {
    if (!error) {
      if (myContent == null) myContent = new StringBuffer();
      if (tagged) {
        final int separatorIndex = message.indexOf(" ");
        if (separatorIndex >= 0) {
          String tagType = message.substring(0, separatorIndex);
          if ("text".equals(tagType)) {
            message = message.substring(separatorIndex + 1);
            myContent.append(message + "\n");
          }
        }
      } else {
        myContent.append(message + "\n");
      }

    } else if (message.startsWith("VERS:")) {
      final String version = message.substring(5).trim();
      myRevision = version;
      myCvsRevisionNumber = new CvsRevisionNumber(version);
    }
  }

  public void binaryMessageSent(final byte[] bytes) {
    myBinaryContent = bytes;
  }
}
