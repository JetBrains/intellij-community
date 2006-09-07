package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;

import java.io.File;

/**
 * author: lesya
 */

public class CheckoutFileOperation extends CvsOperationOnFiles {
  private final File myFile;
  private boolean myIsDirectory;
  private String myModuleName;
  private final RevisionOrDate myRevisionOrDate;
  private final boolean myMakeNewFilesReadOnly;

  public CheckoutFileOperation(VirtualFile parent,
                               CvsConfiguration config,
                               String fileName,
                               Entry entry,
                               boolean makeNewFilesReadOnly,
                               final boolean isDirectory) {
    this(parent, RevisionOrDateImpl.createOn(parent, entry, config.CHECKOUT_DATE_OR_REVISION_SETTINGS), fileName, makeNewFilesReadOnly);
    myIsDirectory = isDirectory;
  }

  public CheckoutFileOperation(final VirtualFile parent,
                               RevisionOrDate revisionOrDate,
                               final String fileName,
                               boolean makeNewFilesReadOnly) {
    super(new CheckoutAdminReader());
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
    myRevisionOrDate = revisionOrDate;
    myFile = CvsVfsUtil.getFileFor(parent, fileName);
    myIsDirectory = myFile.isDirectory();
    Runnable writeAction = new Runnable(){
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                myModuleName = CvsUtil.getModuleName(parent) + "/" + fileName;
              }
            });

          }
        };

    if (!ApplicationManager.getApplication().isDispatchThread()){
      try {
        ApplicationManager.getApplication().invokeAndWait(writeAction, ModalityState.defaultModalityState());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    } else {
      writeAction.run();
    }

    addFile(myFile.getAbsolutePath());
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    CheckoutCommand result = new CheckoutCommand();
    result.setRecursive(true);
    result.addModule(myModuleName);
    myRevisionOrDate.setForCommand(result);
    if (!isRoot()) {
      result.setAlternativeCheckoutDirectory(myIsDirectory ? myFile.getName() : ".");
    }
    return result;
  }

  private boolean isRoot() {
    return new File(myModuleName).getParentFile() == null;
  }

  protected File getAdminRootFor(CvsRootProvider root) {
    return getRoot();
  }

  private File getRoot() {
    return myFile.getParentFile();
  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  protected File getLocalRootFor(CvsRootProvider root) {
    File result = getRoot();
    LOG.assertTrue(result != null);
    return result;
  }

  protected String getOperationName() {
    return "checkout";
  }
}

