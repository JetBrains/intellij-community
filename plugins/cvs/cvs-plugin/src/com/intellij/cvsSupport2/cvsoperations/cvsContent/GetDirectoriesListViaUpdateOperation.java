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
package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderOnStoredRepositoryPath;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

public class GetDirectoriesListViaUpdateOperation extends LocalPathIndifferentOperation implements DirectoryContentProvider {
  private final DirectoryContentListener myDirectoryContentListener = new DirectoryContentListener();
  private Consumer<DirectoryContent> myStepByStepListener;

  public GetDirectoriesListViaUpdateOperation(CvsEnvironment env, final String parentDirectoryName) {
    super(new AdminReaderOnStoredRepositoryPath(createRepositoryPathProvider(parentDirectoryName)), env);
  }

  public static RepositoryPathProvider createRepositoryPathProvider(final String parentDirName) {
    return new RepositoryPathProvider() {
      public String getRepositoryPath(String repository) {
        String result = repository;
        if (!StringUtil.endsWithChar(result, '/')) result += "/";
        return result + parentDirName;
      }
    };

  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setDoNoChanges(true);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    UpdateCommand command = new UpdateCommand();
    command.setBuildDirectories(true);

    root.getRevisionOrDate().setForCommand(command);
    command.setRecursive(true);

    return command;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    final DirectoryContentListener tmp = new DirectoryContentListener();
    tmp.messageSent(message);
    final DirectoryContent tmpContent = tmp.getDirectoryContent();
    final DirectoryContent mainContent = myDirectoryContentListener.getDirectoryContent();
    if (mainContent.getFiles().containsAll(tmpContent.getFiles()) &&
        mainContent.getSubDirectories().containsAll(tmpContent.getSubDirectories()) &&
        mainContent.getSubModules().containsAll(tmpContent.getSubModules())) {
    } else {
      myDirectoryContentListener.getDirectoryContent().copyDataFrom(tmpContent);
      if (myStepByStepListener != null) {
        myStepByStepListener.consume(tmpContent);
      }
    }
  }

  public DirectoryContent getDirectoryContent() {
    return myDirectoryContentListener.getDirectoryContent();
  }

  protected String getOperationName() {
    return "update";
  }

  public void setStepByStepListener(final Consumer<DirectoryContent> stepByStepListener) {
    myStepByStepListener = stepByStepListener;
  }
}
