package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Consumer;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class ChangeGoToChangePopupAction<Chain extends DiffRequestChain>
  extends GoToChangePopupBuilder.BaseGoToChangePopupAction<Chain>{
  public ChangeGoToChangePopupAction(@NotNull Chain chain, @NotNull Consumer onSelected) {
    super(chain, onSelected);
  }

  @NotNull
  @Override
  protected JBPopup createPopup(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();

    Ref<JBPopup> popup = new Ref<JBPopup>();
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
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
}
