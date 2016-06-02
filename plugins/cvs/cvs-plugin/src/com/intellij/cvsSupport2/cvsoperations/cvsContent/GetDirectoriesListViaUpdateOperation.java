/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  private DirectoryContentListener myStreamingDirectoryContentListener = null;
  private long timeStamp = System.currentTimeMillis();
  private Consumer<DirectoryContent> myStreamingListener;

  public GetDirectoriesListViaUpdateOperation(CvsEnvironment env, final String parentDirectoryName) {
    super(new AdminReaderOnStoredRepositoryPath(createRepositoryPathProvider(parentDirectoryName)), env);
    addFinishAction(() -> {
      if (myStreamingListener != null) {
        myStreamingListener.consume(myStreamingDirectoryContentListener.getDirectoryContent());
      }
    });
  }

  public static RepositoryPathProvider createRepositoryPathProvider(final String parentDirName) {
    return new RepositoryPathProvider() {
      @Override
      public String getRepositoryPath(String repository) {
        String result = repository;
        if (!StringUtil.endsWithChar(result, '/')) result += "/";
        return result + parentDirName;
      }
    };
  }

  @Override
  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setDoNoChanges(true);
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final UpdateCommand command = new UpdateCommand();
    command.setBuildDirectories(true);
    command.setRecursive(true);
    root.getRevisionOrDate().setForCommand(command);
    return command;
  }

  @Override
  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    if (myStreamingListener != null) {
      myStreamingDirectoryContentListener.messageSent(message);
      final long timePassed = System.currentTimeMillis() - timeStamp;
      final DirectoryContent streamingContent = myStreamingDirectoryContentListener.getDirectoryContent();
      final int size = streamingContent.getTotalSize();
      if (timePassed > 25L && size > 0) {
        myStreamingListener.consume(streamingContent);
        myStreamingDirectoryContentListener = new DirectoryContentListener();
        timeStamp = System.currentTimeMillis();
      }
    }
    else {
      myDirectoryContentListener.messageSent(message);
    }
  }

  @Override
  public DirectoryContent getDirectoryContent() {
    return myDirectoryContentListener.getDirectoryContent();
  }

  @Override
  protected String getOperationName() {
    return "update";
  }

  @Override
  public void setStreamingListener(final Consumer<DirectoryContent> streamingListener) {
    myStreamingListener = streamingListener;
    myStreamingDirectoryContentListener = new DirectoryContentListener();
  }
}
