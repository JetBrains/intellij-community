/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;

import java.io.File;

/**
 * @author lesya
 */
public class CheckoutFileOperation extends CvsOperationOnFiles {

  private final File myFile;
  private final boolean myIsDirectory;
  private final String myModuleName;
  private final RevisionOrDate myRevisionOrDate;
  private final boolean myMakeNewFilesReadOnly;

  public CheckoutFileOperation(VirtualFile parent,
                               CvsConfiguration config,
                               String fileName,
                               Entry entry,
                               boolean makeNewFilesReadOnly,
                               final boolean isDirectory) {
    this(parent, RevisionOrDateImpl.createOn(parent, entry, config.CHECKOUT_DATE_OR_REVISION_SETTINGS), fileName, makeNewFilesReadOnly,
         isDirectory);
  }

  public CheckoutFileOperation(final VirtualFile parent,
                               RevisionOrDate revisionOrDate,
                               final String fileName,
                               boolean makeNewFilesReadOnly) {
    this(parent, revisionOrDate, fileName, makeNewFilesReadOnly, null);
  }

  private CheckoutFileOperation(final VirtualFile parent,
                                RevisionOrDate revisionOrDate,
                                final String fileName,
                                boolean makeNewFilesReadOnly,
                                Boolean isDirectory/*null means detect*/) {
    super(new CheckoutAdminReader());
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
    myRevisionOrDate = revisionOrDate;
    myFile = CvsVfsUtil.getFileFor(parent, fileName);
    myIsDirectory = isDirectory == null ? myFile.isDirectory() : isDirectory;
    myModuleName = getModuleName(parent, fileName);
    addFile(myFile.getAbsolutePath());
  }

  private static String getModuleName(final VirtualFile parent, final String fileName) {
    final String parentModule = CvsUtil.getModuleName(parent);
    final VirtualFile file = parent.findChild(fileName);
    if (parentModule == null && file != null) {
      return CvsUtil.getModuleName(file);
    }
    else {
      return parentModule + "/" + fileName;
    }
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final CheckoutCommand result = new CheckoutCommand(null);
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

  @Override
  protected File getAdminRootFor(CvsRootProvider root) {
    return getRoot();
  }

  private File getRoot() {
    return myFile.getParentFile();
  }

  @Override
  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  @Override
  protected File getLocalRootFor(CvsRootProvider root) {
    final File result = getRoot();
    LOG.assertTrue(result != null);
    return result;
  }

  @Override
  protected String getOperationName() {
    return "checkout";
  }
}

