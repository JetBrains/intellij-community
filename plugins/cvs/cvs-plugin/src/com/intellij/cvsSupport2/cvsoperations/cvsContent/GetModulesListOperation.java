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
import com.intellij.util.Consumer;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.ListModulesCommand;
import org.netbeans.lib.cvsclient.command.checkout.Module;

import java.util.Collection;

public class GetModulesListOperation extends LocalPathIndifferentOperation implements DirectoryContentProvider{
  private final ListModulesCommand myCommand = new ListModulesCommand();
  private DirectoryContent myStreamingDirectoryContent = null;
  private long timeStamp = System.currentTimeMillis();
  private Consumer<DirectoryContent> myStreamingListener;

  public GetModulesListOperation(CvsEnvironment environment) {
    super(environment);
    addFinishAction(() -> {
      if (myStreamingListener != null && myStreamingDirectoryContent.getTotalSize() > 0) {
        myStreamingListener.consume(myStreamingDirectoryContent);
      }
    });
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    return myCommand;
  }

  public Collection<Module> getModulesInRepository() {
    return myCommand.getModules();
  }

  @Override
  protected String getOperationName() {
    return "checkout";
  }

  @Override
  public DirectoryContent getDirectoryContent() {
    final DirectoryContent result = new DirectoryContent();
    final Collection<Module> modules = myCommand.getModules();
    for (final Module module : modules) {
      result.addModule(module.getModuleName());
    }
    return result;
  }

  @Override
  public void setStreamingListener(final Consumer<DirectoryContent> streamingListener) {
    myStreamingListener = streamingListener;
    myStreamingDirectoryContent = new DirectoryContent();
    myCommand.setModuleConsumer(module -> {
      myStreamingDirectoryContent.addModule(module.getModuleName());
      final long timePassed = System.currentTimeMillis() - timeStamp;
      if (timePassed > 25L) {
        streamingListener.consume(myStreamingDirectoryContent);
        myStreamingDirectoryContent = new DirectoryContent();
        timeStamp = System.currentTimeMillis();
      }
    });
  }
}
