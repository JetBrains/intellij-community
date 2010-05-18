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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

/**
 * @author yole
 */
public class RefreshIncomingChangesAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      doRefresh(project);
    }
  }

  public static void doRefresh(final Project project) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(project);
    cache.hasCachesForAnyRoot(new Consumer<Boolean>() {
      public void consume(final Boolean notEmpty) {
        if ((! notEmpty) && (!CacheSettingsDialog.showSettingsDialog(project))) {
          return;
        }
        cache.refreshAllCachesAsync(true, false);
        cache.refreshIncomingChangesAsync();
      }
    });
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && !CommittedChangesCache.getInstance(project).isRefreshingIncomingChanges());
  }
}