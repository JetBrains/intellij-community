/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.Details;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.GenericDetailsLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcsUtil.UIVcsUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/17/11
 * Time: 7:08 PM
 */

/**
 * @deprecated use {@link CacheChangeProcessor} instead
 */
@Deprecated
public class ShortDiffDetails implements RefreshablePanel<Change>, Disposable {
  private final Project myProject;
  private final VcsChangeDetailsManager myVcsChangeDetailsManager;
  private final Getter<Change[]> myMaster;

  private DetailsPanel myDetailsPanel;
  private GenericDetailsLoader<Change, RefreshablePanel> myDetailsLoader;
  private PairConsumer<Change,RefreshablePanel> myDetailsConsumer;
  private final SLRUMap<FilePath,RefreshablePanel> myDetailsCache;
  private FilePath myCurrentFilePath;
  private JComponent myParent;
  private RefreshablePanel myCurrentPanel;

  public ShortDiffDetails(Project project, Getter<Change[]> master, final VcsChangeDetailsManager vcsChangeDetailsManager) {
    myMaster = master;
    myProject = project;
    myVcsChangeDetailsManager = vcsChangeDetailsManager;

    myDetailsCache = new SLRUMap<FilePath, RefreshablePanel>(10, 10) {
      @Override
      protected void onDropFromCache(FilePath key, RefreshablePanel value) {
        if (value != null) {
          Disposer.dispose(value);
        }
      }
    };
  }

  @Override
  public boolean isStillValid(Change change) {
    return true;
  }

  @Override
  public boolean refreshDataSynch() {
    Change selected = myDetailsLoader.getCurrentlySelected();
    if (selected == null) return false;
    if (myCurrentPanel != null) {
      return myCurrentPanel.refreshDataSynch();
    }
    return false;
  }

  public void setParent(JComponent parent) {
    myParent = parent;
  }

  @Override
  public void dataChanged() {
  }

  @Override
  public void refresh() {
    ensureDetailsCreated();
    myCurrentFilePath = setDetails();
    myDetailsPanel.layout();
  }

  public FilePath getCurrentFilePath() {
    return myCurrentFilePath;
  }

  @Nullable
  private FilePath setDetails() {
    FilePath currentFilePath = null;
    final Change[] selectedChanges = myMaster.get();
    if (selectedChanges.length == 0) {
      myDetailsPanel.nothingSelected();
    } else {
      final String freezed = ChangeListManager.getInstance(myProject).isFreezed();
      if (freezed != null) {
        myDetailsPanel.data(UIVcsUtil.errorPanel(freezed, false));
        return currentFilePath;
      }

      myDetailsPanel.notAvailable();
      for (Change change : selectedChanges) {
        if (change.getBeforeRevision() instanceof FakeRevision || change.getAfterRevision() instanceof FakeRevision) {
          myDetailsPanel.loadingInitial();
          return currentFilePath;
        }
        if (myVcsChangeDetailsManager.canComment(change)) {
          currentFilePath = ChangesUtil.getFilePath(change);
          myDetailsLoader.updateSelection(change, true);
          return currentFilePath;
        }
      }

      myDetailsPanel.notAvailable();
    }
    return currentFilePath;
  }

  @Override
  public JPanel getPanel() {
    ensureDetailsCreated();
    return myDetailsPanel.getPanel();
  }

  @Override
  public void away() {
    //
  }

  private void ensureDetailsCreated() {
    if (myDetailsConsumer != null) return;

    myDetailsPanel = new DetailsPanel();
    final PairConsumer<Change, RefreshablePanel> cacheConsumer = new PairConsumer<Change, RefreshablePanel>() {
      @Override
      public void consume(Change change, RefreshablePanel pair) {
        final FilePath filePath = ChangesUtil.getFilePath(change);
        final RefreshablePanel old = myDetailsCache.get(filePath);
        if (old == null) {
          myDetailsCache.put(filePath, pair);
        } else if (old != pair) {
          if (pair != null) {
            myDetailsCache.put(filePath, pair);
            Disposer.dispose(old);
          }
        }
      }
    };
    myDetailsConsumer = new PairConsumer<Change, RefreshablePanel>() {
      @Override
      public void consume(Change change, RefreshablePanel pair) {
        cacheConsumer.consume(change, pair);
        pair.refresh();
        myCurrentPanel = pair;
        myDetailsPanel.data(myCurrentPanel.getPanel());
        myDetailsPanel.layout();
      }
    };
    myDetailsLoader = new GenericDetailsLoader<Change, RefreshablePanel>(new Consumer<Change>() {
      @Override
      public void consume(Change change) {
        if (myCurrentPanel != null) {
          myCurrentPanel.away();
        }

        final FilePath filePath = ChangesUtil.getFilePath(change);
        RefreshablePanel details = myDetailsCache.get(filePath);
        if (details != null && ! details.isStillValid(change)) {
          Disposer.dispose(details);
          details = null;
          myDetailsLoader.resetValueConsumer();
        }
        if (details != null) {
          myDetailsConsumer.consume(change, details);
        } else {
          final RefreshablePanel detailsPanel = myVcsChangeDetailsManager.getPanel(change, myParent);
          if (detailsPanel != null) {
            try {
              myDetailsLoader.take(change, detailsPanel);
            }
            catch (Details.AlreadyDisposedException e) {
              Disposer.dispose(detailsPanel);
            }
//            myDetailsPanel.layout();
          }
        }
      }
    }, myDetailsConsumer);
    myDetailsLoader.setCacheConsumer(cacheConsumer);
  }

  @Override
  public void dispose() {
    if (myDetailsLoader != null) {
      Disposer.dispose(myDetailsLoader);
    }
    if (myDetailsPanel != null) {
      myDetailsPanel.clear();
    }
    myDetailsCache.clear();
  }

  public boolean refreshData(VirtualFile vf) {
    RefreshablePanel panel = myDetailsCache.get(VcsUtil.getFilePath(vf));
    if (panel != null) {
      panel.dataChanged();
      return true;
    }
    return false;
  }
}
