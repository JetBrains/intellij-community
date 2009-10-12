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
package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
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
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        final String parentModule = CvsUtil.getModuleName(parent);
        VirtualFile file = parent.findChild(fileName);
        if (parentModule == null && file != null) {
          myModuleName = CvsUtil.getModuleName(file);
        }
        else {
          myModuleName = parentModule + "/" + fileName;
        }
      }
    }.execute();

    addFile(myFile.getAbsolutePath());
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    CheckoutCommand result = new CheckoutCommand(null);
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

