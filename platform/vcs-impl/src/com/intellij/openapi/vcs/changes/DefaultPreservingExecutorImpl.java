// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.join;

/**
 * VCS-independent implementation for {@link VcsPreservingExecutor}
 */
class DefaultPreservingExecutorImpl {

  private static final Logger LOG = Logger.getInstance(DefaultPreservingExecutorImpl.class);

  private final Project myProject;
  private final Collection<? extends VirtualFile> myRootsToSave;
  private final String myOperationTitle;
  private final Runnable myOperation;
  private final VcsShelveChangesSaver mySaver;

  DefaultPreservingExecutorImpl(@NotNull Project project,
                                       @NotNull Collection<? extends VirtualFile> rootsToSave,
                                       @NotNull String operationTitle,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull Runnable operation) {
    mySaver = new VcsShelveChangesSaver(project, indicator, operationTitle);
    myProject = project;
    myRootsToSave = rootsToSave;
    myOperationTitle = operationTitle;
    myOperation = operation;
  }

  public void execute() {
    Runnable operation = () -> {
      LOG.debug("starting");
      boolean savedSuccessfully = save();
      LOG.debug("save result: " + savedSuccessfully);
      if (savedSuccessfully) {
        try {
          LOG.debug("running operation");
          myOperation.run();
          LOG.debug("operation completed.");
        }
        finally {
          LOG.debug("loading");
          ProgressManager.getInstance().executeNonCancelableSection(() -> mySaver.load());
        }
      }
      LOG.debug("finished.");
    };

    new VcsFreezingProcess(myProject, myOperationTitle, operation).execute();
  }

  private boolean save() {
    return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      try {
        mySaver.save(myRootsToSave);
        return true;
      }
      catch (VcsException e) {
        LOG.info("Couldn't save local changes", e);
        VcsNotifier.getInstance(myProject).notifyError(
          VcsBundle.message("notification.title.couldn.t.save.uncommitted.changes"),
          String.format("Tried to save uncommitted changes in shelve before %s, but failed with an error.<br/>%s",
                        myOperationTitle, join(e.getMessages())));
        return false;
      }
    });
  }
}
