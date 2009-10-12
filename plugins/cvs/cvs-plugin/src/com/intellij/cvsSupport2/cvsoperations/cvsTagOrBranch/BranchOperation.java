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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.openapi.vcs.FilePath;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

/**
 * author: lesya
 */
public class BranchOperation extends CvsOperationOnFiles{

  private final String myBranchName;
  private final boolean myOverrideExisting;
  private final boolean myIsTag;

  public BranchOperation(FilePath[] files, String branchName,
                         boolean overrideExisting, boolean isTag) {
    myBranchName = branchName;
    myOverrideExisting = overrideExisting;
    myIsTag = isTag;
    for (int i = 0; i < files.length; i++) {
      addFile(files[i].getIOFile());
    }
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    TagCommand result = new TagCommand();
    result.setMakeBranchTag(!myIsTag);
    result.setTag(myBranchName);
    result.setOverrideExistingTag(myOverrideExisting);
    addFilesToCommand(root, result);
    return result;
  }

  protected String getOperationName() {
    return "branch";
  }
}
