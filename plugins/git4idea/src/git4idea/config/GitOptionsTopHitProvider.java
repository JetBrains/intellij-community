// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
final class GitOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
      if ("Git".equals(descriptor.getDisplayName())) {
        final GitVcsSettings settings = GitVcsSettings.getInstance(project);
        ArrayList<BooleanOptionDescription> options = new ArrayList<>();
        options.add(applicationOption("Git: Commit automatically on cherry-pick", "isAutoCommitOnCherryPick", "setAutoCommitOnCherryPick"));
        options.add(option(project, "Git: Auto-update if push of the current branch was rejected", "autoUpdateIfPushRejected", "setAutoUpdateIfPushRejected"));
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        if (manager.moreThanOneRoot()) {
          options.add(new BooleanOptionDescription("Git: Control repositories synchronously", "vcs.Git") {
            @Override
            public boolean isOptionEnabled() {
              return settings.getSyncSetting() == DvcsSyncSettings.Value.SYNC;
            }

            @Override
            public void setOptionState(boolean enabled) {
              settings.setSyncSetting(enabled ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
            }
          });
        }
        options.add(option(project, "Git: Warn if CRLF line separators are about to be committed", "warnAboutCrlf", "setWarnAboutCrlf"));
        options.add(option(project, "Git: Warn when committing in detached HEAD or during rebase", "warnAboutDetachedHead", "setWarnAboutDetachedHead"));
        return Collections.unmodifiableCollection(options);
      }
    }
    return Collections.emptyList();
  }

  private static BooleanOptionDescription option(final Project project, String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "vcs.Git", getter, setter) {
      @Override
      public Object getInstance() {
        return GitVcsSettings.getInstance(project);
      }
    };
  }

  private static BooleanOptionDescription applicationOption(String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "vcs.Git", getter, setter) {
      @Override
      public Object getInstance() {
        return GitVcsApplicationSettings.getInstance();
      }
    };
  }
}
