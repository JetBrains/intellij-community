// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ChangesBrowserBase} extension with Rollback/Revert action added to the toolbar.
 * After the revert completes, the changes list is automatically refreshed according to the actual changes
 * retrieved from the {@link ChangeListManager}.
 */
public class ChangesBrowserWithRollback extends AsyncChangesBrowserBase implements Disposable {
  private final Set<Change> myOriginalChanges;

  public ChangesBrowserWithRollback(@NotNull Project project, @NotNull List<? extends Change> changes) {
    super(project, false, true);
    myOriginalChanges = new HashSet<>(changes);

    new RollbackDialogAction().registerCustomShortcutSet(this, null);

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);
    init();

    myViewer.rebuildTree();
  }

  @Override
  public void dispose() {
    shutdown();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      new RollbackDialogAction()
    );
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      new RollbackDialogAction()
    );
  }

  @NotNull
  @Override
  protected AsyncChangesTreeModel getChangesTreeModel() {
    return SimpleAsyncChangesTreeModel.create(grouping -> {
      Collection<Change> allChanges = ChangeListManager.getInstance(myProject).getAllChanges();
      List<Change> newChanges = ContainerUtil.filter(allChanges, myOriginalChanges::contains);

      RemoteStatusChangeNodeDecorator decorator = RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator();
      return TreeModelBuilder.buildFromChanges(myProject, grouping, newChanges, decorator);
    });
  }


  private class MyChangeListListener extends ChangeListAdapter {
    @NotNull private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("ChangesBrowserWithRollback", 300, true,
                             ChangesBrowserWithRollback.this, ChangesBrowserWithRollback.this);

    private void doUpdate() {
      myUpdateQueue.queue(DisposableUpdate.createDisposable(myUpdateQueue, "update", () -> {
        myViewer.rebuildTree();
      }));
    }

    @Override
    public void changeListsChanged() {
      doUpdate();
    }
  }
}

