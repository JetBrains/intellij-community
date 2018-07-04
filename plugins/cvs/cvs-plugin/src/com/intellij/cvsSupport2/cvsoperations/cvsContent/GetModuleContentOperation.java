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
import com.intellij.cvsSupport2.cvsoperations.common.*;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderOnStoredRepositoryPath;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminWriterStoringRepositoryPath;
import com.intellij.cvsSupport2.javacvsImpl.io.DeafLocalFileWriter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.file.ILocalFileWriter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class GetModuleContentOperation extends CompositeOperation implements DirectoryContentProvider {
  private final DirectoryContentListener myDirectoryContentListener = new DirectoryContentListener();
  private DirectoryContentListener myStreamingDirectoryContentListener = new DirectoryContentListener();
  private final AdminWriterStoringRepositoryPath myAdminWriterStoringRepositoryPath;
  private String myModuleLocation;
  @NonNls private static final Pattern UPDATING_PATTERN = Pattern.compile("cvs .*: Updating (.+)");
  private Consumer<DirectoryContent> myStreamingListener;
  private long timeStamp = System.currentTimeMillis();

  public GetModuleContentOperation(CvsEnvironment environment, final String moduleName) {
    myAdminWriterStoringRepositoryPath = new AdminWriterStoringRepositoryPath(moduleName, environment.getCvsRootAsString());
    addOperation(createExpandingRepositoryPathOperation(myAdminWriterStoringRepositoryPath, environment, moduleName));
    addOperation(createGetModuleContentOperation(myAdminWriterStoringRepositoryPath, environment, moduleName));
  }

  private LocalPathIndifferentOperation createGetModuleContentOperation(RepositoryPathProvider adminWriter,
                                                                        CvsEnvironment environment,
                                                                        final String moduleName) {
    final LocalPathIndifferentOperation operation =
      new LocalPathIndifferentOperation(new AdminReaderOnStoredRepositoryPath(adminWriter), environment) {
        private boolean myIsInModule = false;

        @Override
        protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
          final CheckoutCommand result = new CheckoutCommand(null);
          result.addModule(moduleName);
          result.setRecursive(true);
          return result;
        }

        @Override
        protected String getOperationName() {
          return "checkout";
        }

        @Override
        protected ILocalFileWriter createLocalFileWriter(String cvsRoot,
                                                         UpdatedFilesManager mergedFilesCollector,
                                                         CvsExecutionEnvironment cvsExecutionEnvironment) {
          return DeafLocalFileWriter.INSTANCE;
        }

        @Override
        public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
          super.messageSent(message, byteMessage, error, tagged);
          myStreamingDirectoryContentListener.setModulePath(myAdminWriterStoringRepositoryPath.getModulePath());
          final Matcher matcher = UPDATING_PATTERN.matcher(message);
          if (matcher.matches()) {
            if (myModuleLocation != null && myModuleLocation.equals(matcher.group(1))) {
              myIsInModule = true;
            }
            else {
              myStreamingDirectoryContentListener.messageSent(message);
              myIsInModule = false;
            }
          }
          else if (DirectoryContentListener.moduleMessage_ver1(message)) {
            myIsInModule = true;
          }

          if (myIsInModule) {
            myStreamingDirectoryContentListener.messageSent(message);
          }
          final DirectoryContent streamingDirectoryContent = myStreamingDirectoryContentListener.getDirectoryContent();
          if (myStreamingListener != null) {
            final long timePassed = System.currentTimeMillis() - timeStamp;
            if (streamingDirectoryContent.getTotalSize() > 0 && timePassed > 25L) {
              myStreamingListener.consume(streamingDirectoryContent);
              final DirectoryContentListener newListener = new DirectoryContentListener();
              newListener.setModuleName(myStreamingDirectoryContentListener.getModuleName());
              myStreamingDirectoryContentListener = newListener;
              timeStamp = System.currentTimeMillis();
            }
          }
          else {
            myDirectoryContentListener.getDirectoryContent().copyDataFrom(streamingDirectoryContent);
          }
        }

        @Override
        public void modifyOptions(GlobalOptions options) {
          super.modifyOptions(options);
          options.setDoNoChanges(true);
        }
      };
    operation.addFinishAction(() -> myStreamingListener.consume(myStreamingDirectoryContentListener.getDirectoryContent()));
    return operation;
  }

  private LocalPathIndifferentOperation createExpandingRepositoryPathOperation(
    AdminWriterStoringRepositoryPath adminWriter, CvsEnvironment environment, final String moduleName) {
    return new LocalPathIndifferentOperation(adminWriter, environment) {
      @Override
      protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
        final CheckoutCommand result = new CheckoutCommand(null);
        result.addModule(moduleName);
        result.setRecursive(false);
        return result;
      }

      @Override
      protected ILocalFileWriter createLocalFileWriter(String cvsRoot,
                                                       UpdatedFilesManager mergedFilesCollector,
                                                       CvsExecutionEnvironment cvsExecutionEnvironment) {
        return DeafLocalFileWriter.INSTANCE;
      }

      @Override
      protected String getOperationName() {
        return "checkout";
      }

      @Override
      public void moduleExpanded(String module) {
        super.moduleExpanded(module);
        if (myModuleLocation == null) {
          myModuleLocation = module;
          myStreamingDirectoryContentListener.setModuleName(myModuleLocation);
        }
      }
    };
  }

  @Override
  public DirectoryContent getDirectoryContent() {
    return myDirectoryContentListener.getDirectoryContent();
  }

  @Override
  public void setStreamingListener(Consumer<DirectoryContent> streamingListener) {
    myStreamingListener = streamingListener;
  }
}
