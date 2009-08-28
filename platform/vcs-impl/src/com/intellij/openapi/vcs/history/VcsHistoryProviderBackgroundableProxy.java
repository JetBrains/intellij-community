package com.intellij.openapi.vcs.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.VcsBackgroundableComputable;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

public class VcsHistoryProviderBackgroundableProxy {
  private final Project myProject;
  private final VcsHistoryProvider myDelegate;

  public VcsHistoryProviderBackgroundableProxy(final Project project, final VcsHistoryProvider delegate) {
    myDelegate = delegate;
    myProject = project;
  }

  public void createSessionFor(final FilePath filePath, final Consumer<VcsHistorySession> continuation,
                               @Nullable VcsBackgroundableActions actionKey, final boolean silent) {
    final ThrowableComputable<VcsHistorySession, VcsException> throwableComputable =
      new ThrowableComputable<VcsHistorySession, VcsException>() {
        public VcsHistorySession compute() throws VcsException {
          return myDelegate.createSessionFor(filePath);
        }
      };
    final VcsBackgroundableActions resultingActionKey = actionKey == null ? VcsBackgroundableActions.CREATE_HISTORY_SESSION : actionKey;
    final Object key = VcsBackgroundableActions.keyFrom(filePath);

    if (silent) {
      VcsBackgroundableComputable.createAndRunSilent(myProject, resultingActionKey, key, VcsBundle.message("loading.file.history.progress"),
                                                     throwableComputable, continuation);
    } else {
      VcsBackgroundableComputable.createAndRun(myProject, resultingActionKey, key, VcsBundle.message("loading.file.history.progress"),
      VcsBundle.message("message.title.could.not.load.file.history"), throwableComputable, continuation, null);
    }
  }
}
