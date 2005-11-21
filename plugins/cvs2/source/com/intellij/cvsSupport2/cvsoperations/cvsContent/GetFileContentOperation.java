package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.CvsBundle;
import com.intellij.util.text.LineReader;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.ExportCommand;
import org.netbeans.lib.cvsclient.file.FileObject;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GetFileContentOperation extends LocalPathIndifferentOperation {

  private final static byte NOT_LOADED = 0;
  private final static byte FILE_NOT_FOUND = 1;
  private final static byte DELETED = 2;
  private final static byte SUCCESSFULLY_LOADED = 3;
  private final static byte LOADING = 4;

  private byte myState = NOT_LOADED;

  private byte[] myFileBytes = null;
  private String myRevision;
  private String myModuleName;
  private File myTempDirectory;
  private CvsRootProvider myRoot;
  private final boolean myFileIsBinary;
  private CvsRevisionNumber myCvsRevisionNumber;
  private RevisionOrDate myRevisionOrDate;

  public static GetFileContentOperation createForFile(VirtualFile file, RevisionOrDate revisionOrDate)
    throws CannotFindCvsRootException {
    File ioFile = CvsVfsUtil.getFileFor(file);
    return new GetFileContentOperation(new File(getPathInRepository(file)),
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
    myFileIsBinary = FileTypeManager.getInstance().getFileTypeByFileName(cvsFile.getName()).isBinary();
    myCvsRevisionNumber = myRevisionOrDate.getCvsRevisionNumber();
  }

  private static String getPathInRepository(VirtualFile file) {
    return CvsUtil.getModuleName(file);
  }

  protected synchronized Collection getAllCvsRoots() {
    return Collections.singleton(myRoot);
  }

  public synchronized String getRevisionString() {
    if (myCvsRevisionNumber != null) {
      return myCvsRevisionNumber.asString();
    } else if (myRevisionOrDate != null){
      return myRevisionOrDate.toString();
    } else {
      return CvsBundle.message("cvs.unknown.revision.presentation");
    }
  }

  protected synchronized Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    myState = LOADING;
    try {
      myTempDirectory = FileUtil.createTempDirectory("checkout", "cvs");
      myTempDirectory.deleteOnExit();
      LOG.assertTrue(myTempDirectory.isDirectory());
    }
    catch (IOException e) {
      cvsExecutionEnvironment.getErrorProcessor().addError(new CvsException("Could not create temp directory:"
                                                                            + e.getLocalizedMessage(), root.getCvsRootAsString())
                                                           );
      return null;
    }
    myRoot.changeLocalRootTo(myTempDirectory);
    myRoot.changeAdminRootTo(myTempDirectory);
    ExportCommand command = new ExportCommand();
    command.setRecursive(false);
    command.addModule(myModuleName);

    myRevisionOrDate.setForCommand(command);

    return command;
  }

  public synchronized String getRevision() {
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

  public synchronized boolean isDeleted() {
    if (myState == LOADING) {
      getFileBytes();
    }
    return myState == DELETED;
  }

  private synchronized byte[] loadFileBytes() {
    try {
      LOG.assertTrue(myState == LOADING, "state = " + String.valueOf(myState));
      LOG.assertTrue(myTempDirectory != null);
      LOG.assertTrue(myTempDirectory.isDirectory());
      File file = getFile();
      if (!file.isFile()) {
        myState = FILE_NOT_FOUND;
        return new byte[0];
      }
      else {
        myState = SUCCESSFULLY_LOADED;
      }
      if (myFileIsBinary) {
        return loadBinaryContent(file);
      }
      else {
        return loadTextContent(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return new byte[0];
    }
    finally {
      delete(myTempDirectory);
    }
  }

  private File getFile() {
    return new File(myTempDirectory, myModuleName);
  }

  private void delete(File fileToDelete) {
    if (fileToDelete == null) return;
    if (!fileToDelete.exists()) return;
    File[] files = fileToDelete.listFiles();
    if (files != null) {
      for (File file : files) {
        delete(file);
      }
    }
    if (!FileUtil.delete(fileToDelete)) fileToDelete.deleteOnExit();
  }

  private byte[] loadTextContent(File file) throws IOException {
    //noinspection IOResourceOpenedButNotSafelyClosed
    List<byte[]> lines = new LineReader().readLines(new FileInputStream(file));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    for (Iterator<byte[]> each = lines.iterator(); each.hasNext();) {
      byte[] bytes = each.next();
      buffer.write(bytes);
      if (each.hasNext()) buffer.write('\n');
    }
    buffer.flush();
    return buffer.toByteArray();
  }

  private byte[] loadBinaryContent(File file) throws IOException {
    return FileUtil.loadFileBytes(file);
  }

  public synchronized void gotEntry(FileObject abstractFileObject, Entry entry) {
    if (entry == null) {
      myState = DELETED;
      myFileBytes = new byte[0];
      delete(myTempDirectory);
    }
    else {
      myRevision = entry.getRevision();
      myCvsRevisionNumber = new CvsRevisionNumber(myRevision);
    }
  }

  public synchronized boolean fileNotFound() {
    getFileBytes();
    return myState == FILE_NOT_FOUND;
  }

  public synchronized boolean isLoaded() {
    return myState != NOT_LOADED;
  }

  public synchronized CvsRevisionNumber getRevisionNumber() {
    LOG.assertTrue(myCvsRevisionNumber != null);
    return myCvsRevisionNumber;
  }

  protected String getOperationName() {
    return "checkout";
  }
}
