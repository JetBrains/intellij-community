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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsUser;
import git4idea.DialogManager;
import git4idea.history.GitHistoryUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class GitRebaseOverMergeProblem {
  private static final Logger LOG = Logger.getInstance(GitRebaseOverMergeProblem.class);
  public static final String DESCRIPTION =
    "You are about to rebase merge commits. \n" +
    "This can lead to duplicate commits in history, or even data loss.\n" +
    "It is recommended to merge instead of rebase in this case.";

  public enum Decision {
    MERGE_INSTEAD("Merge"),
    REBASE_ANYWAY("Rebase Anyway"),
    CANCEL_OPERATION(CommonBundle.getCancelButtonText());

    private final String myButtonText;

    Decision(@NotNull String buttonText) {
      myButtonText = buttonText;
    }

    @NotNull
    private static String[] getButtonTitles() {
      return ContainerUtil.map2Array(values(), String.class, new Function<Decision, String>() {
        @Override
        public String fun(Decision decision) {
          return decision.myButtonText;
        }
      });
    }

    @NotNull
    public static Decision getOption(final int index) {
      return ObjectUtils.assertNotNull(ContainerUtil.find(values(), new Condition<Decision>() {
        @Override
        public boolean value(Decision decision) {
          return decision.ordinal() == index;
        }
      }));
    }

    private static int getDefaultButtonIndex() {
      return MERGE_INSTEAD.ordinal();
    }

    private static int getFocusedButtonIndex() {
      return CANCEL_OPERATION.ordinal();
    }
  }

  public static boolean hasProblem(@NotNull Project project,
                                   @NotNull VirtualFile root,
                                   @NotNull String baseRef,
                                   @NotNull String currentRef) {
    final Ref<Boolean> mergeFound = Ref.create(Boolean.FALSE);
    Consumer<TimedVcsCommit> detectingConsumer = new Consumer<TimedVcsCommit>() {
      @Override
      public void consume(TimedVcsCommit commit) {
        mergeFound.set(true);
      }
    };

    String range = baseRef + ".." + currentRef;
    try {
      GitHistoryUtils.readCommits(project, root, Arrays.asList(range, "--merges"),
                                  EmptyConsumer.<VcsUser>getInstance(), EmptyConsumer.<VcsRef>getInstance(), detectingConsumer);
    }
    catch (VcsException e) {
      LOG.warn("Couldn't get git log --merges " + range, e);
    }
    return mergeFound.get();
  }

  @NotNull
  public static Decision showDialog() {
    final Ref<Decision> decision = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        decision.set(doShowDialog());
      }
    }, ModalityState.defaultModalityState());
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
