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
package git4idea.ui;

import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * {@link ChangesBrowser} extension with Rollback/Revert action added to the toolbar.
 * After the revert completes, the changes list is automatically refreshed according to the actual changes
 * retrieved from the {@link ChangeListManager}.
 */
public class ChangesBrowserWithRollback extends ChangesBrowser {
  private final List<Change> myOriginalChanges;

  public ChangesBrowserWithRollback(@NotNull Project project, @NotNull List<Change> changes) {
    super(project, null, changes, null, false, true, null, MyUseCase.LOCAL_CHANGES, null);
    myOriginalChanges = changes;
    RollbackDialogAction rollback = new RollbackDialogAction();
    EmptyAction.setupAction(rollback, IdeActions.CHANGES_VIEW_ROLLBACK, this);
    addToolbarAction(rollback);
    setChangesToDisplay(changes);
  }

  @Override
  public void rebuildList() {
    if (myOriginalChanges != null) { // null is possible because rebuildList is called during initialization
      myChangesToDisplay = filterActualChanges(myProject, myOriginalChanges);
    }
    super.rebuildList();
  }

  @NotNull
  private static List<Change> filterActualChanges(@NotNull Project project, @NotNull List<Change> originalChanges) {
    final Collection<Change> allChanges = ChangeListManager.getInstance(project).getAllChanges();
    return ContainerUtil.filter(originalChanges, new Condition<Change>() {
      @Override
      public boolean value(Change change) {
        return allChanges.contains(change);
      }
    });
  }
}
