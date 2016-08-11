/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class ShowSelectedChangeAction extends ShowChangeAbstractAction {

  private static final Logger LOG = Logger.getInstance(ShowSelectedChangeAction.class);

  private JBPopup myPopup;

  @Override
  protected boolean isEnabled(@NotNull ChangeRequestChain chain) {
    return getChangesFromRequests(chain.getAllRequests()).size() > 1;
  }

  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull Project project, @NotNull ChangeRequestChain chain,
                                 @NotNull DiffViewer diffViewer) {
    List<DiffRequestPresentable> requests = chain.getAllRequests();
    List<Change> changes = getChangesFromRequests(requests);

    ChangesBrowser cb = new MyChangesBrowser(project, changes, requests, chain, diffViewer);

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(cb, cb.getPreferredFocusedComponent())
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnOtherWindowOpen(true)
      .setMovable(true)
      .setCancelKeyEnabled(true)
      .setCancelOnClickOutside(true)
      .createPopup();

    InputEvent event = e.getInputEvent();
    if (event instanceof MouseEvent) {
      myPopup.show(new RelativePoint((MouseEvent)event));
    }
    else {
      myPopup.showInBestPositionFor(e.getDataContext());
    }
  }

  @NotNull
  private static List<Change> getChangesFromRequests(@NotNull List<DiffRequestPresentable> requests) {
    List<Change> changes = new ArrayList<>();
    for (DiffRequestPresentable step : requests) {
      Change change = getChange(step);
      if (change != null) {
        changes.add(change);
      }
    }
    return changes;
  }

  @Nullable
  private static Change getChange(@NotNull DiffRequestPresentable presentable) {
    if (presentable instanceof DiffRequestPresentableProxy) {
      try {
        presentable = ((DiffRequestPresentableProxy)presentable).init();
      }
      catch (VcsException e) {
        LOG.info(e);
        return null;
      }
    }
    if (presentable instanceof ChangeDiffRequestPresentable) {
      return ((ChangeDiffRequestPresentable)presentable).getChange();
    }
    return null;
  }

  private class MyChangesBrowser extends ChangesBrowser implements Runnable {
    private final List<DiffRequestPresentable> myRequests;
    private final ChangeRequestChain myChain;
    private final DiffViewer myDiffViewer;

    public MyChangesBrowser(@NotNull Project project, @NotNull List<Change> changes, @NotNull List<DiffRequestPresentable> requests,
                            @NotNull ChangeRequestChain chain, @NotNull DiffViewer diffViewer) {
      super(project, null, changes, null, false, false, null, MyUseCase.LOCAL_CHANGES, null);
      myRequests = requests;
      myChain = chain;
      myDiffViewer = diffViewer;

      setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      setChangesToDisplay(changes);
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
      DiffRequestPresentable selectedStep = findSelectedStep(change);
      if (selectedStep != null) {
        DiffRequest newRequest = myChain.moveTo(selectedStep);
        openRequest(myDiffViewer, newRequest);
      }
      myPopup.cancel();
    }

    @Nullable
    private DiffRequestPresentable findSelectedStep(@Nullable Change change) {
      for (DiffRequestPresentable step : myRequests) {
        Change c = getChange(step);
        if (c != null && c.equals(change)) {
          return step;
        }
      }
      return null;
    }
  }
}
