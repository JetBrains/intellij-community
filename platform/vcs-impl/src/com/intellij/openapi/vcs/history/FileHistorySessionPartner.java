/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.VcsInternalDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.ContentUtilEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.vcs.history.FileHistoryPanelImpl.sameHistories;

public class FileHistorySessionPartner implements VcsHistorySessionConsumer, Disposable {

  @NotNull private final AbstractVcs myVcs;
  @NotNull private final VcsHistoryProvider myVcsHistoryProvider;
  @NotNull private final FilePath myPath;
  @Nullable private final VcsRevisionNumber myStartingRevisionNumber;
  @NotNull private final LimitHistoryCheck myLimitHistoryCheck;
  @NotNull private final BufferedListConsumer<VcsFileRevision> myBuffer;
  @NotNull private final FileHistoryPanelImpl myFileHistoryPanel;

  private volatile VcsAbstractHistorySession mySession;

  public FileHistorySessionPartner(@NotNull VcsHistoryProvider vcsHistoryProvider,
                                   @NotNull FilePath path,
                                   @Nullable VcsRevisionNumber startingRevisionNumber,
                                   @NotNull AbstractVcs vcs,
                                   @NotNull FileHistoryRefresherI refresher) {
    myVcsHistoryProvider = vcsHistoryProvider;
    myPath = path;
    myStartingRevisionNumber = startingRevisionNumber;
    myLimitHistoryCheck = new LimitHistoryCheck(vcs.getProject(), path.getPath());
    myVcs = vcs;
    myFileHistoryPanel = createFileHistoryPanel(new EmptyHistorySession(), refresher);

    Consumer<List<VcsFileRevision>> sessionRefresher = vcsFileRevisions -> {
      // TODO: Logic should be revised to just append some revisions to history panel instead of creating and showing new history session
      mySession.getRevisionList().addAll(vcsFileRevisions);
      VcsHistorySession copy = mySession.copyWithCachedRevision();
      ApplicationManager.getApplication().invokeAndWait(() -> myFileHistoryPanel.setHistorySession(copy));
    };
    myBuffer = new BufferedListConsumer<VcsFileRevision>(5, sessionRefresher, 1000) {
      @Override
      protected void invokeConsumer(@NotNull Runnable consumerRunnable) {
        // Do not invoke in arbitrary background thread as due to parallel execution this could lead to cases when invokeLater() (from
        // sessionRefresher) is scheduled at first for history session with (as an example) 10 revisions (new buffered list) and then with
        // 5 revisions (previous buffered list). And so incorrect UI is shown to the user.
        consumerRunnable.run();
      }
    };

    Disposer.register(myFileHistoryPanel, this);
  }

  @Nullable
  static FileHistoryRefresherI findExistingHistoryRefresher(@NotNull Project project,
                                                            @NotNull FilePath path,
                                                            @Nullable VcsRevisionNumber startingRevisionNumber) {
    JComponent component = ContentUtilEx.findContentComponent(getToolWindow(project).getContentManager(), comp ->
      comp instanceof FileHistoryPanelImpl &&
      sameHistories((FileHistoryPanelImpl)comp, path, startingRevisionNumber));
    return component == null ? null : VcsInternalDataKeys.FILE_HISTORY_REFRESHER.getData((DataProvider)component);
  }

  @CalledInBackground
  public boolean shouldBeRefreshed() {
    return mySession.shouldBeRefreshed();
  }

  public void acceptRevision(VcsFileRevision revision) {
    myLimitHistoryCheck.checkNumber();
    myBuffer.consumeOne(revision);
  }

  @NotNull
  private FileHistoryPanelImpl createFileHistoryPanel(@NotNull VcsHistorySession session, @NotNull FileHistoryRefresherI refresher) {
    ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(myVcs.getProject()).getContentManager();
    return new FileHistoryPanelImpl(myVcs, myPath, myStartingRevisionNumber, session, myVcsHistoryProvider, contentManager, refresher,
                                    false);
  }

  public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
    if (mySession != null && session != null && mySession.getRevisionList().equals(session.getRevisionList())) return;
    mySession = session;
    if (mySession != null) {
      mySession.shouldBeRefreshed();  // to init current revision!

      List<VcsFileRevision> revisionList = mySession.getRevisionList();
      while (myLimitHistoryCheck.isOver(revisionList.size())) revisionList.remove(revisionList.size() - 1);
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (mySession != null && !mySession.getRevisionList().isEmpty()) {
        myFileHistoryPanel.setHistorySession(mySession.copyWithCachedRevision());
      }
    });
  }

  @NotNull
  private static ToolWindow getToolWindow(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    assert toolWindow != null : "Version Control ToolWindow should be available at this point.";
    return toolWindow;
  }

  public void reportException(VcsException exception) {
    VcsBalloonProblemNotifier.showOverVersionControlView(myVcs.getProject(),
                                                         VcsBundle.message("message.title.could.not.load.file.history") + ": " +
                                                         exception.getMessage(), MessageType.ERROR);
  }

  @Override
  public void beforeRefresh() {
    myLimitHistoryCheck.reset();
  }

  public void createOrSelectContent() {
    ToolWindow toolWindow = getToolWindow(myVcs.getProject());
    ContentManager manager = toolWindow.getContentManager();
    boolean selectedExistingContent = ContentUtilEx.selectContent(manager, myFileHistoryPanel, true);
    if (!selectedExistingContent) {
      String tabName = myPath.getName();
      if (myStartingRevisionNumber != null) {
        tabName += " (" + VcsUtil.getShortRevisionString(myStartingRevisionNumber) + ")";
      }
      ContentUtilEx.addTabbedContent(manager, myFileHistoryPanel, "History", tabName, true);
    }
    toolWindow.activate(null);
  }

  @Override
  public void finished() {
    myBuffer.flush();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (mySession == null) {
        // nothing to be done, exit
        return;
      }
      myFileHistoryPanel.finishRefresh();
    });
  }

  @Override
  public void dispose() {
  }
}
