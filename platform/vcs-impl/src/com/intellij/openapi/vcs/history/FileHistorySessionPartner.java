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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
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
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.vcs.history.FileHistoryPanelImpl.sameHistories;

public class FileHistorySessionPartner implements VcsAppendableHistorySessionPartner {

  @NotNull private final AbstractVcs myVcs;
  @NotNull private final VcsHistoryProvider myVcsHistoryProvider;
  @NotNull private final FilePath myPath;
  @Nullable private final VcsRevisionNumber myStartingRevisionNumber;
  @NotNull private final LimitHistoryCheck myLimitHistoryCheck;
  @NotNull private final FileHistoryRefresherI myRefresherI;
  @NotNull private final BufferedListConsumer<VcsFileRevision> myBuffer;

  private FileHistoryPanelImpl myFileHistoryPanel;
  private volatile VcsAbstractHistorySession mySession;

  public FileHistorySessionPartner(@NotNull VcsHistoryProvider vcsHistoryProvider,
                                   @NotNull FilePath path,
                                   @Nullable VcsRevisionNumber startingRevisionNumber,
                                   @NotNull AbstractVcs vcs,
                                   @NotNull FileHistoryRefresherI refresherI) {
    myVcsHistoryProvider = vcsHistoryProvider;
    myPath = path;
    myStartingRevisionNumber = startingRevisionNumber;
    myLimitHistoryCheck = new LimitHistoryCheck(vcs.getProject(), path.getPath());
    myVcs = vcs;
    myRefresherI = refresherI;
    Consumer<List<VcsFileRevision>> sessionRefresher = vcsFileRevisions -> {
      // TODO: Logic should be revised to just append some revisions to history panel instead of creating and showing new history session
      mySession.getRevisionList().addAll(vcsFileRevisions);
      VcsHistorySession copy = mySession.copyWithCachedRevision();
      ApplicationManager.getApplication().invokeAndWait(() -> ensureHistoryPanelCreated().getHistoryPanelRefresh().consume(copy));
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
  }

  @Nullable
  static FileHistoryRefresherI findExistingHistoryRefresher(@NotNull Project project,
                                                            @NotNull FilePath path,
                                                            @Nullable VcsRevisionNumber startingRevisionNumber) {
    JComponent component = ContentUtilEx.findContentComponent(getToolWindow(project).getContentManager(), comp ->
      comp instanceof FileHistoryPanelImpl &&
      sameHistories((FileHistoryPanelImpl)comp, path, startingRevisionNumber));
    return component == null ? null : ((FileHistoryPanelImpl)component).getRefresher();
  }

  public void acceptRevision(VcsFileRevision revision) {
    myLimitHistoryCheck.checkNumber();
    myBuffer.consumeOne(revision);
  }

  @CalledInAwt
  @NotNull
  private FileHistoryPanelImpl ensureHistoryPanelCreated() {
    if (myFileHistoryPanel == null) {
      myFileHistoryPanel = createFileHistoryPanel(mySession.copyWithCachedRevision());
    }
    return myFileHistoryPanel;
  }

  @NotNull
  private FileHistoryPanelImpl createFileHistoryPanel(@NotNull VcsHistorySession copy) {
    ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(myVcs.getProject()).getContentManager();
    return new FileHistoryPanelImpl(myVcs, myPath, myStartingRevisionNumber, copy, myVcsHistoryProvider, contentManager, myRefresherI, false);
  }

  public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
    if (mySession != null && session != null && mySession.getRevisionList().equals(session.getRevisionList())) return;
    mySession = session;
    if (mySession != null) {
      mySession.shouldBeRefreshed();  // to init current revision!
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      final VcsHistorySession copy = mySession.copyWithCachedRevision();
      if (myFileHistoryPanel == null) {
        myFileHistoryPanel = createFileHistoryPanel(copy);
        createOrSelectContentIfNeeded();
      }
      else if (session != null && !session.getRevisionList().isEmpty()){
        myFileHistoryPanel.getHistoryPanelRefresh().consume(copy);
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
    if (myFileHistoryPanel != null) {
      createOrSelectContentIfNeeded();
    }
  }

  private void createOrSelectContentIfNeeded() {
    ToolWindow toolWindow = getToolWindow(myVcs.getProject());
    if (myRefresherI.isFirstTime()) {
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
  }

  public void finished() {
    myBuffer.flush();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (mySession == null) {
        // nothing to be done, exit
        return;
      }
      ensureHistoryPanelCreated().getHistoryPanelRefresh().finished();
    });
  }

  @Override
  public void forceRefresh() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (mySession == null) {
        // nothing to be done, exit
        return;
      }
      ensureHistoryPanelCreated().scheduleRefresh(false);
    });
  }
}
