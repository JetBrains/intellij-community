package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;

final class ShelvedTreeModelBuilder extends TreeModelBuilder {
  private ShelvedTreeModelBuilder(Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
    super(project, grouping);
  }

  public void setShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists) {
    createShelvedListsWithChangesNode(shelvedLists, myRoot);
  }

  public void setDeletedShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists) {
    createShelvedListsWithChangesNode(shelvedLists, createTagNode(VcsBundle.message("shelve.recently.deleted.node")));
  }

  private void createShelvedListsWithChangesNode(@NotNull List<ShelvedChangeList> shelvedLists,
                                                 @NotNull ChangesBrowserNode<?> parentNode) {
    for (ShelvedChangeList changeList : shelvedLists) {
      ShelvedListNode shelvedListNode = new ShelvedListNode(changeList);
      insertSubtreeRoot(shelvedListNode, parentNode);

      List<ShelvedChange> changes = changeList.getChanges();
      if (changes == null) continue;

      List<ShelvedWrapper> shelvedChanges = new ArrayList<>();
      changes.stream().map(change -> new ShelvedWrapper(change, changeList)).forEach(shelvedChanges::add);
      changeList.getBinaryFiles().stream().map(binaryChange -> new ShelvedWrapper(binaryChange, changeList)).forEach(shelvedChanges::add);

      shelvedChanges.sort(comparing(s -> s.getChangeWithLocal(myProject), CHANGE_COMPARATOR));

      for (ShelvedWrapper shelved : shelvedChanges) {
        Change change = shelved.getChangeWithLocal(myProject);
        FilePath filePath = ChangesUtil.getFilePath(change);
        insertChangeNode(change, shelvedListNode, new ShelvedChangeNode(shelved, filePath, change.getOriginText(myProject)));
      }
    }
  }
}
