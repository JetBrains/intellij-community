// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFileOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCommit.CommitFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.RemoveFilesOperation;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryModificationOperation extends CompositeOperation {
  private final CvsOperationOnFiles myRemoveRequests;
  private final CommitFilesOperation myCommitRequests;
  private int myAddedFilesCount = 0;

  public static RepositoryModificationOperation
      createGlobalTransactionOperation(String message, CvsConfiguration configuration) {
    return new RepositoryModificationOperation(message, configuration);
  }

  private RepositoryModificationOperation(String message, CvsConfiguration configuration) {
    myRemoveRequests = new RemoveFilesOperation();
    myCommitRequests = new CommitFilesOperation(message, configuration.MAKE_NEW_FILES_READONLY);
    addOperation(myRemoveRequests);
  }


  public void commitFile(String path) {
    commitFile(new File(path).getAbsoluteFile());
  }

  private void commitFile(File file) {
    myCommitRequests.addFile(file.getAbsolutePath());
  }

  public void addFile(String folderPath, String name, KeywordSubstitution substitution) {
    myAddedFilesCount++;
    File file = new File(folderPath, name).getAbsoluteFile();
    AddFileOperation addFilesOperation = new AddFileOperation(substitution);
    addFilesOperation.addFile(file.getAbsolutePath());
    addOperation(addFilesOperation);
    if (file.isFile())
      commitFile(file);
  }

  public void removeFile(String path) {
    File file = new File(path).getAbsoluteFile();
    myRemoveRequests.addFile(file.getAbsolutePath());
    commitFile(file);
  }

  @Override
  public int getFilesToProcessCount() {
    int filesToRemove = myRemoveRequests.getFilesCount();
    int filesToChange = myCommitRequests.getFilesCount() - myAddedFilesCount - filesToRemove;

    return myAddedFilesCount * 4 +
        filesToRemove * 2 +
        filesToChange * 2;
  }

  public void setMessage(Object parameters) {
    myCommitRequests.setMessage(parameters);
  }

  @Override
  protected List<CvsOperation> getSubOperations() {
    ArrayList<CvsOperation> result = new ArrayList<>(super.getSubOperations());
    result.add(myCommitRequests);
    return result;
  }
}
