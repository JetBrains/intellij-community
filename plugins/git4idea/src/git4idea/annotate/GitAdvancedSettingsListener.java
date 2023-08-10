// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;


public final class GitAdvancedSettingsListener {

  public static void registerListener(@NotNull Project project, @NotNull Disposable disposable) {
    ApplicationManager.getApplication().getMessageBus()
      .connect(disposable)
      .subscribe(AdvancedSettingsChangeListener.TOPIC, new AdvancedSettingsChangeListener() {
        @Override
        public void advancedSettingChanged(@NotNull String id, @NotNull Object oldValue, @NotNull Object newValue) {
          if (id.equals(GitFileUtils.READ_CONTENT_WITH)) {
            ProjectLevelVcsManager.getInstance(project).getContentRevisionCache().clearConstantCache();
          }
        }
      });
  }
}
