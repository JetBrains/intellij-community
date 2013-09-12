/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import git4idea.GitVcs;
import git4idea.history.wholeTree.GitLog;
import git4idea.history.wholeTree.LogFactoryService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GitProjectLogManager {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.browser.GitProjectLogManager");
  public static final String CONTENT_KEY = "Log";

  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final LogFactoryService myLogFactoryService;

  private final AtomicReference<Content> myCurrentContent;
  private final AtomicReference<GitLog> myLogRef;
  private VcsListener myListener;

  public static final Topic<CurrentBranchListener> CHECK_CURRENT_BRANCH =
            new Topic<CurrentBranchListener>("CHECK_CURRENT_BRANCH", CurrentBranchListener.class);
  private CurrentBranchListener myCurrentBranchListener;
  private MessageBusConnection myConnection;

  public GitProjectLogManager(final Project project, final ProjectLevelVcsManager vcsManager, final LogFactoryService logFactoryService) {
    myProject = project;
    myVcsManager = vcsManager;
    myLogFactoryService = logFactoryService;
    myCurrentContent = new AtomicReference<Content>();
    myLogRef = new AtomicReference<GitLog>();

    myListener = new VcsListener() {
      public void directoryMappingChanged() {
        invokeLaterRecalculateWindows();
      }
    };
    myCurrentBranchListener = new CurrentBranchListener() {
      public void consume(VirtualFile file) {
        /*final VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir == null) return;
        final Map<VirtualFile, Content> currentState = myComponentsMap.get();
        for (VirtualFile virtualFile : currentState.keySet()) {
          if (Comparing.equal(virtualFile, file)) {
            final String title = getCaption(baseDir, virtualFile);
            final Content content = currentState.get(virtualFile);
            if (! Comparing.equal(title, content.getDisplayName())) {
              new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
                public void run() {
                  content.setDisplayName(title);
                }
              }.callMe();
            }
            return;
          }
        }*/
      }
    };
  }

  public static GitProjectLogManager getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, GitProjectLogManager.class);
  }

  public void deactivate() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    myVcsManager.removeVcsListener(myListener);
    if (myCurrentContent.get() != null) {
      final ChangesViewContentI cvcm = ChangesViewContentManager.getInstance(myProject);
      cvcm.removeContent(myCurrentContent.get());
      myCurrentContent.set(null);
    }
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  public void activate() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    myVcsManager.addVcsListener(myListener);
    invokeLaterRecalculateWindows();
    myConnection = myProject.getMessageBus().connect(myProject);
    myConnection.subscribe(CHECK_CURRENT_BRANCH, myCurrentBranchListener);
  }

  private void invokeLaterRecalculateWindows() {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        recalculateWindows();
      }
    }.callMe();
  }

  private void recalculateWindows() {
    final GitVcs vcs = GitVcs.getInstance(myProject);
    final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(vcs);
    final List<VirtualFile> fileList = Arrays.asList(roots);

    final ChangesViewContentI cvcm = ChangesViewContentManager.getInstance(myProject);
    final Content currContent = myCurrentContent.get();
    if (currContent != null) {
      myLogRef.get().rootsChanged(fileList);
      return;
    }
    final GitLog gitLog = myLogFactoryService.createComponent(true);
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(gitLog.getVisualComponent(), CONTENT_KEY, false);
    content.setCloseable(false);
    cvcm.addContent(content);
    Disposer.register(content, gitLog);
    myLogRef.set(gitLog);

    myCurrentContent.set(content);
    gitLog.rootsChanged(fileList);
  }

  public GitLog getLog() {
    return myLogRef.get();
  }

  public interface CurrentBranchListener extends Consumer<VirtualFile> {
  }
}
