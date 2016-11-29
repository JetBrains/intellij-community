/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE;
import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE;

public class VcsDiffUtil {

  @CalledInAwt
  public static void showDiffFor(@NotNull Project project,
                                 @NotNull final Collection<Change> changes,
                                 @NotNull final String revNumTitle1,
                                 @NotNull final String revNumTitle2,
                                 @NotNull final FilePath filePath) {
    if (filePath.isDirectory()) {
      showChangesDialog(project, getDialogTitle(filePath, revNumTitle1, revNumTitle2), ContainerUtil.newArrayList(changes));
    }
    else {
      if (changes.isEmpty()) {
        DiffManager.getInstance().showDiff(project, new MessageDiffRequest("No Changes Found"));
      }
      else {
        final HashMap<Key, Object> revTitlesMap = new HashMap<>(2);
        revTitlesMap.put(VCS_DIFF_LEFT_CONTENT_TITLE, revNumTitle1);
        revTitlesMap.put(VCS_DIFF_RIGHT_CONTENT_TITLE, revNumTitle2);
        ShowDiffContext showDiffContext = new ShowDiffContext() {
          @NotNull
          @Override
          public Map<Key, Object> getChangeContext(@NotNull Change change) {
            return revTitlesMap;
          }
        };
        ShowDiffAction.showDiffForChange(project, changes, 0, showDiffContext);
      }
    }
  }

  @NotNull
  private static String getDialogTitle(@NotNull final FilePath filePath, @NotNull final String revNumTitle1,
                                       @NotNull final String revNumTitle2) {
    return String.format("Difference between %s and %s versions in %s", revNumTitle1, revNumTitle2, filePath.getName());
  }

  @NotNull
  public static String getRevisionTitle(@NotNull String revision, boolean localMark) {
    return revision +
           (localMark ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
  }

  @CalledInAwt
  public static void showChangesDialog(@NotNull Project project, @NotNull String title, @NotNull List<Change> changes) {
    DialogBuilder dialogBuilder = new DialogBuilder(project);

    dialogBuilder.setTitle(title);
    dialogBuilder.setActionDescriptors(new DialogBuilder.CloseDialogAction());
    final ChangesBrowser changesBrowser =
      new ChangesBrowser(project, null, changes, null, false, true, null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
    changesBrowser.setChangesToDisplay(changes);
    dialogBuilder.setCenterPanel(changesBrowser);
    dialogBuilder.setPreferredFocusComponent(changesBrowser.getPreferredFocusedComponent());
    dialogBuilder.showNotModal();
  }

  @NotNull
  public static List<Change> createChangesWithCurrentContentForFile(@NotNull FilePath filePath,
                                                                    @Nullable ContentRevision beforeContentRevision) {
    return Collections.singletonList(new Change(beforeContentRevision, CurrentContentRevision.create(filePath)));
  }
}
