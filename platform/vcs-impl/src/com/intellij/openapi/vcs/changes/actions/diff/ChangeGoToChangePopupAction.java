package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Consumer;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ChangeGoToChangePopupAction<Chain extends DiffRequestChain>
  extends GoToChangePopupBuilder.BaseGoToChangePopupAction<Chain>{
  public ChangeGoToChangePopupAction(@NotNull Chain chain, @NotNull Consumer<Integer> onSelected) {
    super(chain, onSelected);
  }

  @NotNull
  @Override
  protected JBPopup createPopup(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();

    Ref<JBPopup> popup = new Ref<>();
    ChangesBrowser cb = new MyChangesBrowser(project, getChanges(), getCurrentSelection(), popup);

    popup.set(JBPopupFactory.getInstance()
                .createComponentPopupBuilder(cb, cb.getPreferredFocusedComponent())
                .setResizable(true)
                .setModalContext(false)
                .setFocusable(true)
                .setRequestFocus(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelOnOtherWindowOpen(true)
                .setMovable(true)
                .setCancelKeyEnabled(true)
                .setCancelOnClickOutside(true)
                .setDimensionServiceKey(project, "Diff.GoToChangePopup", false)
                .createPopup());

    return popup.get();
  }

  //
  // Abstract
  //

  protected abstract int findSelectedStep(@Nullable Change change);

  @NotNull
  protected abstract List<Change> getChanges();

  @Nullable
  protected abstract Change getCurrentSelection();

  //
  // Helpers
  //

  private class MyChangesBrowser extends ChangesBrowser implements Runnable {
    @NotNull private final Ref<JBPopup> myPopup;

    public MyChangesBrowser(@NotNull Project project,
                            @NotNull List<Change> changes,
                            @Nullable final Change currentChange,
                            @NotNull Ref<JBPopup> popup) {
      super(project, null, changes, null, false, false, null, MyUseCase.LOCAL_CHANGES, null);
      setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      setChangesToDisplay(changes);

      UiNotifyConnector.doWhenFirstShown(this, new Runnable() {
        @Override
        public void run() {
          if (currentChange != null) select(Collections.singletonList(currentChange));
        }
      });

      myPopup = popup;
    }

    @Override
    protected void buildToolBar(DefaultActionGroup toolBarGroup) {
      // remove diff action
    }

    @NotNull
    @Override
    protected Runnable getDoubleClickHandler() {
      return this;
    }

    @Override
    public void run() {
      Change change = getSelectedChanges().get(0);
      final int index = findSelectedStep(change);
      myPopup.get().cancel();
      IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
        @Override
        public void run() {
          //noinspection unchecked
          myOnSelected.consume(index);
        }
      });
    }
  }

  public abstract static class Fake<Chain extends DiffRequestChain> extends ChangeGoToChangePopupAction<Chain> {
    @NotNull private final List<Change> myChanges;
    private final int mySelection;

    @SuppressWarnings("AbstractMethodCallInConstructor")
    public Fake(@NotNull Chain chain, int selection, @NotNull Consumer<Integer> onSelected) {
      super(chain, onSelected);

      mySelection = selection;

      // we want to show ChangeBrowser-based popup, so have to create some fake changes
      List<? extends DiffRequestProducer> requests = chain.getRequests();

      myChanges = new ArrayList<>(requests.size());
      for (int i = 0; i < requests.size(); i++) {
        FilePath path = getFilePath(i);
        FileStatus status = getFileStatus(i);
        FakeContentRevision revision = new FakeContentRevision(path);
        myChanges.add(new Change(revision, revision, status));
      }
    }

    @NotNull
    protected abstract FilePath getFilePath(int index);

    @NotNull
    protected abstract FileStatus getFileStatus(int index);

    @Override
    protected int findSelectedStep(@Nullable Change change) {
      return myChanges.indexOf(change);
    }

    @NotNull
    @Override
    protected List<Change> getChanges() {
      return myChanges;
    }

    @Nullable
    @Override
    protected Change getCurrentSelection() {
      if (mySelection < 0 || mySelection >= myChanges.size()) return null;
      return myChanges.get(mySelection);
    }

    private static class FakeContentRevision implements ContentRevision {
      @NotNull private final FilePath myFilePath;

      public FakeContentRevision(@NotNull FilePath filePath) {
        myFilePath = filePath;
      }

      @Nullable
      @Override
      public String getContent() throws VcsException {
        return null;
      }

      @NotNull
      @Override
      public FilePath getFile() {
        return myFilePath;
      }

      @NotNull
      @Override
      public VcsRevisionNumber getRevisionNumber() {
        return VcsRevisionNumber.NULL;
      }
    }
  }
}
