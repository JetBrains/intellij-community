package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsUpdatePolicy;
import com.intellij.cvsSupport2.cvshandlers.UpdateHandler;
import com.intellij.cvsSupport2.updateinfo.UpdatedFilesProcessor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdateSessionAdapter;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CvsStatusEnvironment implements UpdateEnvironment {
  private final Project myProject;

  public CvsStatusEnvironment(Project project) {
    myProject = project;

  }

  public void fillGroups(UpdatedFiles updatedFiles) {
    CvsUpdatePolicy.fillGroups(updatedFiles);
  }

  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] contentRoots, final UpdatedFiles updatedFiles, ProgressIndicator progressIndicator) {
    final UpdateSettings updateSettings = UpdateSettings.DONT_MAKE_ANY_CHANGES;
    final UpdateHandler handler = CommandCvsHandler.createUpdateHandler(contentRoots,
                                                                        updateSettings, myProject, updatedFiles);
    handler.addCvsListener(new UpdatedFilesProcessor(myProject, updatedFiles));
    CvsOperationExecutor cvsOperationExecutor = new CvsOperationExecutor(true, myProject, ModalityState.defaultModalityState());
    cvsOperationExecutor.setShowErrors(false);
    cvsOperationExecutor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    final CvsResult result = cvsOperationExecutor.getResult();
    return new UpdateSessionAdapter(result.getErrorsAndWarnings(), result.isCanceled());
  }

  public Configurable createConfigurable(Collection<FilePath> files) {
    return null;
  }

}
