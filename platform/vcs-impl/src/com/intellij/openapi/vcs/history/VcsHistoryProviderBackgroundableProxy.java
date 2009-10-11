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
