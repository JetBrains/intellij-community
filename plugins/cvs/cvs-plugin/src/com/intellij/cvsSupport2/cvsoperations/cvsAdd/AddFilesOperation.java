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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd;

import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.util.HashMap;
import java.util.Map;

/**
 * author: lesya
 */
public class AddFilesOperation extends CompositeOperation {

  private final Map<KeywordSubstitution, AddFileOperation> mySubstitutionToOperation = new HashMap<>();
  private final Map<VirtualFile, AddFileOperation> myAlreadyProcessedParentToOperation = new HashMap<>();
  private int myFilesCount = -1;
  private int myMainPartSize;

  public AddFilesOperation() {
    myMainPartSize = 0;
  }

  public void addFile(VirtualFile file, KeywordSubstitution keywordSubstitution) {
    if (file.isDirectory()) {

      AddFileOperation op = getOperationForFile(file);
      op.addFile(file);
      if (! containsSubOperation(op)) {
        if (myMainPartSize == getSubOperationsCount()) {
          addOperation(op);
        } else {
          addOperation(myMainPartSize, op);
        }
        ++myMainPartSize;
      }

    }
    else {
      if (! mySubstitutionToOperation.containsKey(keywordSubstitution)) {
        final AddFileOperation substitutionOperation = new AddFileOperation(keywordSubstitution);
        mySubstitutionToOperation.put(keywordSubstitution, substitutionOperation);
        addOperation(substitutionOperation);
      }
      mySubstitutionToOperation.get(keywordSubstitution).addFile(file);
    }
  }

  private AddFileOperation getOperationForFile(VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return new AddFileOperation(null);
    }
    if (!myAlreadyProcessedParentToOperation.containsKey(parent)) {
      myAlreadyProcessedParentToOperation.put(parent, new AddFileOperation(null));
    }
    return myAlreadyProcessedParentToOperation.get(parent);
  }

  public int getFilesToProcessCount() {
    if (myFilesCount == -1){
      myFilesCount = calculateAllFilesCount();
    }
    return myFilesCount;
  }

  private int calculateAllFilesCount() {
    int result = 0;
    for (final CvsOperation cvsOperation : getSubOperations()) {
      AddFileOperation addFileOperation = (AddFileOperation)cvsOperation;
      result += addFileOperation.getFilesCount();
    }
    return result;
  }
}
