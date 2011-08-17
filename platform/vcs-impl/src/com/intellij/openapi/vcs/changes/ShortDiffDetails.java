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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.GenericDetailsLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/17/11
 * Time: 7:08 PM
 */
public class ShortDiffDetails implements RefreshablePanel, Disposable {
  private final Project myProject;
  private final VcsChangeDetailsManager myVcsChangeDetailsManager;
  private final Getter<Change[]> myMaster;

  private DetailsPanel myDetailsPanel;
  private GenericDetailsLoader<Change, Pair<RefreshablePanel, Disposable>> myDetailsLoader;
  private PairConsumer<Change,Pair<RefreshablePanel, Disposable>> myDetailsConsumer;
  private final SLRUMap<FilePath, Pair<RefreshablePanel, Disposable>> myDetailsCache;
  private FilePath myCurrentFilePath;

  public ShortDiffDetails(Project project, Getter<Change[]> master, final VcsChangeDetailsManager vcsChangeDetailsManager) {
    myMaster = master;
    myProject = project;
    myVcsChangeDetailsManager = vcsChangeDetailsManager;

    myDetailsCache = new SLRUMap<FilePath, Pair<RefreshablePanel, Disposable>>(10, 10) {
      @Override
      protected void onDropFromCache(FilePath key, Pair<RefreshablePanel, Disposable> value) {
        if (value.getSecond() != null) {
          Disposer.dispose(value.getSecond());
        }
      }
    };
  }

  @Override
  public void refresh() {
    ensureDetailsCreated();
    myCurrentFilePath = setDetails();
    myDetailsPanel.layout();
  }

  public boolean removeFromCache(final VirtualFile vf) {
    return myDetailsCache.remove(new FilePathImpl(vf));
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
    return myDetailsPanel.myPanel;
  }

  private void ensureDetailsCreated() {
    if (myDetailsConsumer != null) return;

    myDetailsPanel = new DetailsPanel();
    final PairConsumer<Change, Pair<RefreshablePanel, Disposable>> cacheConsumer = new PairConsumer<Change, Pair<RefreshablePanel, Disposable>>() {
      @Override
      public void consume(Change change, Pair<RefreshablePanel, Disposable> pair) {
        final FilePath filePath = ChangesUtil.getFilePath(change);
        final Pair<RefreshablePanel, Disposable> old = myDetailsCache.get(filePath);
        if (old == null) {
          myDetailsCache.put(filePath, pair);
        } else if (old != pair) {
          if (pair.getSecond() != null) {
            Disposer.dispose(pair.getSecond());
          }
        }
      }
    };
    myDetailsConsumer = new PairConsumer<Change, Pair<RefreshablePanel, Disposable>>() {
      @Override
      public void consume(Change change, Pair<RefreshablePanel, Disposable> pair) {
        cacheConsumer.consume(change, pair);
        pair.getFirst().refresh();
        myDetailsPanel.data(pair.getFirst().getPanel());
        myDetailsPanel.layout();
      }
    };
    myDetailsLoader = new GenericDetailsLoader<Change, Pair<RefreshablePanel, Disposable>>(new Consumer<Change>() {
      @Override
      public void consume(Change change) {
        final FilePath filePath = ChangesUtil.getFilePath(change);
        Pair<RefreshablePanel, Disposable> details = myDetailsCache.get(filePath);
        if (details != null) {
          myDetailsConsumer.consume(change, details);
        } else if (myVcsChangeDetailsManager.getPanel(change, myDetailsLoader)) {
          myDetailsPanel.loading();
          myDetailsPanel.layout();
        }
      }
    }, myDetailsConsumer);
    myDetailsLoader.setCacheConsumer(cacheConsumer);
  }

  @Override
  public void dispose() {
    myDetailsLoader.dispose();
    myDetailsPanel.clear();
    myDetailsCache.clear();
  }

  private static class DetailsPanel {
    private CardLayout myLayout;
    private JPanel myPanel;
    private JPanel myDataPanel;
    private Layer myCurrentLayer;

    private DetailsPanel() {
      myPanel = new JPanel();
      myLayout = new CardLayout();
      myPanel.setLayout(myLayout);
      myDataPanel = new JPanel(new BorderLayout());

      myPanel.add(UIVcsUtil.errorPanel("No details available", false), Layer.notAvailable.name());
      myPanel.add(UIVcsUtil.errorPanel("Nothing selected", false), Layer.nothingSelected.name());
      myPanel.add(UIVcsUtil.errorPanel("Changes content is not loaded yet", false), Layer.notLoadedInitial.name());
      myPanel.add(UIVcsUtil.errorPanel("Loading...", false), Layer.loading.name());
      myPanel.add(myDataPanel, Layer.data.name());
    }

    public void nothingSelected() {
      myCurrentLayer = Layer.nothingSelected;
    }

    public void notAvailable() {
      myCurrentLayer = Layer.notAvailable;
    }

    public void loading() {
      myCurrentLayer = Layer.loading;
    }

    public void loadingInitial() {
      myCurrentLayer = Layer.notLoadedInitial;
    }

    public void data(final JPanel panel) {
      myCurrentLayer = Layer.data;
      myPanel.add(panel, Layer.data.name());
    }

    public void layout() {
      myLayout.show(myPanel, myCurrentLayer.name());
    }

    public void clear() {
      myPanel.removeAll();
    }

    private static enum Layer {
      notAvailable,
      nothingSelected,
      notLoadedInitial,
      loading,
      data,
    }
  }
}
