package com.intellij.openapi.vcs.changes;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;
import java.util.Map;

public class EditorDiffsManager  {

  public static EditorDiffsManager getInstance(Project project)  {
    return ServiceManager.getService(project, EditorDiffsManager.class);
  }

  private Project myProject;

  private Map<Change, VirtualFile> myDiffEditors = new THashMap<>(new TObjectHashingStrategy<Change>() {
    @Override
    public int computeHashCode(Change change) {
      int fileHashCode = change.hashCode();
      ContentRevision beforeRevision = change.getBeforeRevision();
      int beforeRevisionHash = beforeRevision != null ? beforeRevision.getRevisionNumber().hashCode() : 0;

      ContentRevision afterRevision = change.getAfterRevision();
      int afterRevisionHash = afterRevision != null ? afterRevision.getRevisionNumber().hashCode() : 0;
      return fileHashCode + beforeRevisionHash * 19 + afterRevisionHash * 31;
    }

    @Override
    public boolean equals(Change o1, Change o2) {
      if (o1 == o2) return true;

      if (!o1.equals(o2)) {
        return false;
      }

      final ContentRevision br1 = o1.getBeforeRevision();
      final ContentRevision br2 = o2.getBeforeRevision();
      final ContentRevision ar1 = o1.getAfterRevision();
      final ContentRevision ar2 = o2.getAfterRevision();

      VcsRevisionNumber fbr1 = br1 != null ? br1.getRevisionNumber() : null;
      VcsRevisionNumber fbr2 = br2 != null ? br2.getRevisionNumber() : null;

      VcsRevisionNumber far1 = ar1 != null ? ar1.getRevisionNumber() : null;
      VcsRevisionNumber far2 = ar2 != null ? ar2.getRevisionNumber() : null;

      return Comparing.equal(fbr1, fbr2) && Comparing.equal(far1, far2);
    }
  });

  EditorDiffsManager(Project project) {
    myProject = project;
  }

  public void showDiffInEditor(Change change) {
    VirtualFile virtualFile = myDiffEditors.get(change);
    if (virtualFile == null) {

      ArrayList<ChangeDiffRequestChain.Producer> producers = new ArrayList<>();
      producers.add(ChangeDiffRequestProducer.create(myProject, change));
      DiffRequestChain chain = new ChangeDiffRequestChain(producers, 0);
      VirtualFile changeVirtualFile = change.getVirtualFile();
      String name = changeVirtualFile != null ? changeVirtualFile.getName() : "Diff";
      virtualFile = new ChainDiffVirtualFile(chain, name);

      myDiffEditors.put(change, virtualFile);
    }

    FileEditor[] fileEditors = FileEditorManager.getInstance(myProject).openFile(virtualFile, false, true);
    assert fileEditors.length == 1 : "opened more than one file for the diff";
  }
}
