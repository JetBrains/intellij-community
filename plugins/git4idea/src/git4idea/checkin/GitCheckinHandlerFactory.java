/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.util.PairConsumer;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prohibits commiting with an empty messages.
 * @author Kirill Likhodedov
*/
public class GitCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public GitCheckinHandlerFactory() {
    super(GitVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    return new CheckinHandler() {
      @Override
      public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (panel.getCommitMessage().trim().isEmpty()) {
          Messages.showMessageDialog(panel.getComponent(), GitBundle.message("git.commit.message.empty"),
                                     GitBundle.message("git.commit.message.empty.title"), Messages.getErrorIcon());
          return ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }
    };
  }
}
