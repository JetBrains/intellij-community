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

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.command.add.AddCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

import java.util.List;

public class AddFileOperation extends CvsOperationOnFiles {
  private final KeywordSubstitution myKeywordSubstitution;

  public AddFileOperation(KeywordSubstitution keywordSubstitution) {
    myKeywordSubstitution = keywordSubstitution;
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    AddCommand result = new AddCommand();
    result.setKeywordSubst(myKeywordSubstitution);
    addFilesToCommand(root, result);
    return result;
  }

  @Override
  protected void addFilesToCommand(CvsRootProvider root, AbstractCommand command) {
    super.addFilesToCommand(root, command);
    List<AbstractFileObject> fileObjects = command.getFileObjects();
    for (final AbstractFileObject fileObject: fileObjects) {
      if (fileObject.getParent() == null) {
        LOG.error("Local Root: " + getLocalRootFor(root) + ", Files: " + myFiles);
      }
    }
  }

  @Override
  protected String getOperationName() {
    return "add";
  }

  @Override
  public boolean runInReadThread() {
    return false;
  }
}
