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
package git4idea.config;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class GitOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(Project project) {
    if (ProjectLevelVcsManager.getInstance(project).getAllVcss().length == 0) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(Arrays.asList(
      option(project, "Git: Commit automatically on cherry-pick", "isAutoCommitOnCherryPick", "setAutoCommitOnCherryPick"),
      option(project, "Git: Auto-update if push of the current branch was rejected", "autoUpdateIfPushRejected", "setAutoUpdateIfPushRejected"),
      option(project, "Git: Warn if CRLF line separators are about to be committed", "warnAboutCrlf", "setWarnAboutCrlf"),
      option(project, "Git: Warn when committing in detached HEAD or during rebase", "warnAboutDetachedHead", "setWarnAboutDetachedHead")));
  }

  private static BooleanOptionDescription option(final Project project, String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "vcs.Git", getter, setter) {
      @Override
      public Object getInstance() {
        return GitVcsSettings.getInstance(project);
      }
    };
  }
}
