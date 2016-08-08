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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFileOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCommit.CommitFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.RemoveFilesOperation;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepositoryModificationOperation extends CompositeOperation {
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

  protected List<CvsOperation> getSubOperations() {
    ArrayList<CvsOperation> result = new ArrayList<>(super.getSubOperations());
    result.add(myCommitRequests);
    return result;
  }
}
