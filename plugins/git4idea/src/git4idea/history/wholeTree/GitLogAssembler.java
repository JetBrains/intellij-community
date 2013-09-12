/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.List;

/**
 * @author irengrig
 */
public class GitLogAssembler implements GitLog {
  private final Project myProject;
  private final boolean myProjectScope;
  private GitLogUI myGitLogUI;
  private MediatorImpl myMediator;
  private DetailsLoaderImpl myDetailsLoader;
  private DetailsCache myDetailsCache;
  private LoadController myLoadController;
  private BigTableTableModel myTableModel;
  private boolean myInitialized;

  //@CalledInAwt
  public GitLogAssembler(final Project project, boolean projectScope, final GitCommitsSequentially gitCommitsSequentially) {
    myProject = project;
    myProjectScope = projectScope;
    myMediator = new MediatorImpl(myProject, gitCommitsSequentially);

    myGitLogUI = new GitLogUI(myProject, myMediator);
    myTableModel = myGitLogUI.getTableModel();

    final BackgroundTaskQueue queue = new BackgroundTaskQueue(project, "Git log details");
    myDetailsLoader = new DetailsLoaderImpl(myProject, queue);
    myDetailsCache = new DetailsCache(myProject, myGitLogUI.getUIRefresh(), myDetailsLoader, queue);
    myDetailsLoader.setDetailsCache(myDetailsCache);
    myGitLogUI.setDetailsCache(myDetailsCache);
    myGitLogUI.createMe();
    myGitLogUI.setProjectScope(projectScope);

    // modality state?
    myLoadController = new LoadController(myProject, myMediator, myDetailsCache, gitCommitsSequentially);

    myMediator.setLoader(myLoadController);
    myMediator.setTableModel(myTableModel);
    myMediator.setUIRefresh(myGitLogUI.getRefreshObject());
    myMediator.setDetailsLoader(myDetailsLoader);

    myTableModel.setCache(myDetailsCache);
    Disposer.register(this, myGitLogUI);
  }

  @Override
  public JComponent getVisualComponent() {
    return myGitLogUI.getPanel();
  }

  @Override
  public void setModalityState(ModalityState state) {
    myDetailsCache.setModalityState(state);
    myDetailsLoader.setModalityState(state);
  }

  @Override
  public void selectCommit(String commitId) {
    myGitLogUI.selectCommit(commitId);
  }

  @Override
  public void rootsChanged(List<VirtualFile> roots) {
    myGitLogUI.rootsChanged(roots);
    if (myProjectScope && ! myInitialized) {
      myInitialized = true;
      myGitLogUI.initFromSettings();
    }
  }

  @Override
  public void dispose() {
  }
}
