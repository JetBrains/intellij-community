/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.update;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.history.GitLogUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitRebaseOverMergeProblem {
  private static final Logger LOG = Logger.getInstance(GitRebaseOverMergeProblem.class);
  public static final String DESCRIPTION =
    "You are about to rebase a merge commit with conflicts.\n\n" +
    "Choose 'Merge' if you don't want to resolve conflicts again, " +
    "or you still can rebase if you want to linearize the history.";

  public enum Decision {
    MERGE_INSTEAD("Merge"),
    REBASE_ANYWAY("Rebase"),
    CANCEL_OPERATION(CommonBundle.getCancelButtonText());

    private final String myButtonText;

    Decision(@NotNull String buttonText) {
      myButtonText = buttonText;
    }

    @NotNull
    private static String[] getButtonTitles() {
      return ContainerUtil.map2Array(values(), String.class, decision -> decision.myButtonText);
    }

    @NotNull
    public static Decision getOption(final int index) {
      return ObjectUtils.assertNotNull(ContainerUtil.find(values(), decision -> decision.ordinal() == index));
    }

    private static int getDefaultButtonIndex() {
      return MERGE_INSTEAD.ordinal();
    }

    private static int getFocusedButtonIndex() {
      return REBASE_ANYWAY.ordinal();
    }
  }

  public static boolean hasProblem(@NotNull Project project,
                                   @NotNull VirtualFile root,
                                   @NotNull String baseRef,
                                   @NotNull String currentRef) {
    String range = baseRef + ".." + currentRef;
    try {
      List<GitCommit> commits = GitLogUtil.collectFullDetails(project, root, range, "--merges");
      return StreamEx.of(commits).anyMatch(commit -> !commit.getChanges().isEmpty());
    }
    catch (VcsException e) {
      LOG.warn("Couldn't get git log --merges " + range, e);
      return false;
    }
  }

  @NotNull
  public static Decision showDialog() {
    final Ref<Decision> decision = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> decision.set(doShowDialog()));
    return decision.get();
  }

  @NotNull
  private static Decision doShowDialog() {
    int decision = DialogManager.showMessage(DESCRIPTION, "Rebasing Merge Commits", Decision.getButtonTitles(),
                                             Decision.getDefaultButtonIndex(),
                                             Decision.getFocusedButtonIndex(), Messages.getWarningIcon(), null);
    return Decision.getOption(decision);
  }
}
