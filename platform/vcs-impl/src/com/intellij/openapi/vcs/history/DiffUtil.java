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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DiffUtil {

  public static void showDiffFor(@NotNull Project project,
                                 @NotNull final Collection<Change> changes,
                                 @NotNull final VcsRevisionNumber revNum1,
                                 @NotNull final VcsRevisionNumber revNum2,
                                 @NotNull final FilePath filePath) {
    if (filePath.isDirectory()) {
      showChangesDialog(project, getDialogTitle(filePath, revNum1, revNum2), ContainerUtil.newArrayList(changes));
    }
    else {
      if (changes.isEmpty()) {
        DiffManager.getInstance().showDiff(project, new MessageDiffRequest("No Changes Found"));
      }
      else {
        ShowDiffAction.showDiffForChange(project, changes);
      }
    }
  }

  @NotNull
  private static String getDialogTitle(@NotNull final FilePath filePath, @NotNull final VcsRevisionNumber revNum1,
                                       @Nullable final VcsRevisionNumber revNum2) {
    String rev1Title = revNum1.asString();
    final String filePathName = filePath.getName();
    return revNum2 != null
           ? String.format("Difference between %s and %s in %s", rev1Title, revNum2.asString(), filePathName)
           : String.format("Difference between %s and local version in %s", rev1Title, filePathName);
  }


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
