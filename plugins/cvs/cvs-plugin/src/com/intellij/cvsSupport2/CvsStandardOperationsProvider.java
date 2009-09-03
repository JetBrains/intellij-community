/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryModificationOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.io.File;
import java.util.Map;

public class CvsStandardOperationsProvider {

  private RepositoryModificationOperation myCurrentTransaction;
  private final Project myProject;

  public CvsStandardOperationsProvider(Project project) {
    myProject = project;
  }

  public void checkinFile(String path, Object parameters, Map userData) throws VcsException {
    getCurrentTransaction().commitFile(path);
  }

  public void addFile(String folderPath, String name, Object parameters, Map userData) throws VcsException {
    KeywordSubstitution substitution = null;
    if (parameters instanceof KeywordSubstitution) {
      substitution = (KeywordSubstitution)parameters;
    }
    getCurrentTransaction().addFile(folderPath, name, substitution);
  }

  public void removeFile(String path, Object parameters, Map userData) throws VcsException {
    getCurrentTransaction().removeFile(path);
  }

  public void addDirectory(String parentPath, String name, Object parameters, Map userData) throws VcsException {
    addFile(parentPath, name, parameters, userData);
  }

  public void removeDirectory(String path, Object parameters, Map userData) throws VcsException {
  }

  private RepositoryModificationOperation getCurrentTransaction() {
    if (myCurrentTransaction == null) createTransaction();
    return myCurrentTransaction;
  }

  public void createTransaction() {
    myCurrentTransaction =
    RepositoryModificationOperation.createGlobalTransactionOperation(null, CvsConfiguration.getInstance(myProject));
  }

  public int getFilesToProcessCount() {
    return getCurrentTransaction().getFilesToProcessCount();
  }

  public void commit(Object parameters) throws VcsException {
    getCurrentTransaction().setMessage(parameters);
    CvsVcs2.executeOperation(CvsBundle.message("operation.name.commit.changes"), getCurrentTransaction(), myProject);
    myCurrentTransaction = null;
  }

  public void rollback() {
    myCurrentTransaction = null;
  }

  public byte[] getFileContent(String path) throws VcsException {
    try {
      GetFileContentOperation command = GetFileContentOperation.createForFile(CvsVfsUtil.findFileByIoFile(new File(path)));
      CvsVcs2.executeOperation(CvsBundle.message("operation.name.get.file.content"), command, myProject);
      return command.getFileBytes();
    }
    catch (CannotFindCvsRootException cannotFindCvsRootException) {
      throw new VcsException(cannotFindCvsRootException);
    }
  }
}
