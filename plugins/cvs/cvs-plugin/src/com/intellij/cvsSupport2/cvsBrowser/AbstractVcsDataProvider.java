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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.DefaultCvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetDirectoriesListViaUpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;

import java.util.List;

public abstract class AbstractVcsDataProvider implements RemoteResourceDataProvider {
  protected final CvsEnvironment myEnvironment;
  private Consumer<VcsException> myErrorCallback;

  protected AbstractVcsDataProvider(CvsEnvironment environment) {
    myEnvironment = environment;
  }

  @Override
  public void fillContentFor(final GetContentCallback callback, Consumer<VcsException> errorCallback) {
    myErrorCallback = errorCallback;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      executeCommand(createDirectoryContentProvider(callback.getElementPath()), callback);
    } else {
      final DirectoryContentProvider provider = createDirectoryContentProvider(callback.getElementPath());
      provider.setStreamingListener(directoryContent -> callback.appendDirectoryContent(directoryContent));
      executeCommand(provider, callback);
    }
  }

  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new GetDirectoriesListViaUpdateOperation(myEnvironment, path);
  }

  private static class CancellableCvsHandler extends CommandCvsHandler {
    private CancellableCvsHandler(final String title, final CvsOperation cvsOperation) {
      super(title, cvsOperation, true);
    }

    @Override
    protected boolean runInReadThread() {
      return false;
    }

    // in order to allow progress listener retrieval
    @Override
    public CvsListenerWithProgress getProgressListener() {
      return super.getProgressListener();
    }
  }

  private void executeCommand(final DirectoryContentProvider command, final GetContentCallback callback) {
    final CvsOperationExecutor executor = new CvsOperationExecutor(false, callback.getProject(), callback.getModalityState());
    executor.setIsQuietOperation(true);

    final CancellableCvsHandler cvsHandler = new CancellableCvsHandler(CvsBundle.message("browse.repository.operation.name"), (CvsOperation)command);
    callback.useForCancel(cvsHandler.getProgressListener());

    executor.performActionSync(cvsHandler, new DefaultCvsOperationExecutorCallback() {
      @Override
      public void executionFinished(boolean successfully) {
        if (!successfully) {
          final List<VcsException> errors = cvsHandler.getErrorsExceptAborted();
          if (!errors.isEmpty()) {
            myErrorCallback.consume(errors.get(0));
          }
        }
        callback.finished();
      }
    });
  }
}
