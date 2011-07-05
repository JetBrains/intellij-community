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
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.Details;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.actions.DiffRequestFromChange;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.HashMap;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * todo extract interface and create stub for dummy project
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 5:36 PM
 */
public class VcsChangeDetailsManager {
  private static final int extraLines = 2;
  private final Map<VcsKey, VcsChangeDetailsProvider> myProviderMap = new HashMap<VcsKey, VcsChangeDetailsProvider>();
  private final List<VcsChangeDetailsProvider> myDedicatedList;
  // todo also check for size
  private final LinkedList<DiffPanel> myDiffPanelCache;
  private final Project myProject;
  private Details<Change, Pair<JPanel, Disposable>> myDetails;
  private final BackgroundTaskQueue myQueue;

  public VcsChangeDetailsManager(final Project project) {
    myProject = project;
    myQueue = new BackgroundTaskQueue(myProject, "Loading change details");
    myDedicatedList = new ArrayList<VcsChangeDetailsProvider>();

    myDiffPanelCache = new LinkedList<DiffPanel>();
    myDedicatedList.add(new BinaryDiffDetailsProvider(project, myDiffPanelCache));
    myDedicatedList.add(new FragmentedDiffDetailsProvider(project, new FragmentedDiffRequestFromChange(project), myDiffPanelCache));

    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myQueue.clear();
        for (DiffPanel diffPanel : myDiffPanelCache) {
          Disposer.dispose(diffPanel);
        }
      }
    });
  }

  public void setDetails(Details<Change, Pair<JPanel, Disposable>> details) {
    myDetails = details;
  }

  public boolean canComment(final Change change) {
    return getProvider(change) != null;
  }

  @Nullable
  private VcsChangeDetailsProvider getProvider(final Change change) {
    for (VcsChangeDetailsProvider provider : myDedicatedList) {
      if (provider.canComment(change)) return provider;
    }
    return null;
  }

  // true -> loading
  public boolean getPanel(final Change change) {
    // text details
    final VcsChangeDetailsProvider<?> provider = getProvider(change);
    if (provider == null) {
      return false;
    }

    myQueue.run(new LoaderTask(myProject, provider, change, myDetails));
    return true;
  }

  private static class LoaderTask<T> extends Task.Backgroundable {
    private T myResult;
    private final VcsChangeDetailsProvider<T> myProvider;
    private final Change myChange;
    private final Details<Change, Pair<JPanel, Disposable>> myDetails;

    private LoaderTask(@Nullable Project project, final VcsChangeDetailsProvider provider, final Change change,
                       final Details<Change, Pair<JPanel, Disposable>> consumer) {
      super(project, provider.getProgressTitle(), false, BackgroundFromStartOption.getInstance());
      myProvider = provider;
      myChange = change;
      myDetails = consumer;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (myProject.isDisposed() || ! myProject.isOpen() || !Comparing.equal(myChange, myDetails.getCurrentlySelected())) return;
      //if (! myProvider.canComment(myChange)) return;
      myResult = myProvider.load(myChange);
    }

    @Override
    public void onSuccess() {
      if (myProject.isDisposed() || ! myProject.isOpen()) return;
      if (myResult != null) {
        final Pair<JPanel, Disposable> pair = myProvider.comment(myChange, myResult);
        myDetails.take(myChange, pair);
      }
      // todo else?
    }
  }

  private static class BinaryDiffDetailsProvider implements VcsChangeDetailsProvider<ValueWithVcsException<List<BeforeAfter<DiffContent>>>> {
    private final BinaryDiffRequestFromChange myRequestFromChange;
    private final Project myProject;
    private DiffPanelHolder myDiffPanelHolder;

    private BinaryDiffDetailsProvider(Project project, LinkedList<DiffPanel> diffPanelCache) {
      myProject = project;
      myRequestFromChange = new BinaryDiffRequestFromChange(myProject);
      myDiffPanelHolder = new DiffPanelHolder(diffPanelCache, project);
    }

    @Override
    public String getProgressTitle() {
      return "Loading change content";
    }

    @Override
    public boolean canComment(Change change) {
      return myRequestFromChange.canCreateRequest(change);
    }

    @Override
    public Pair<JPanel, Disposable> comment(Change change, ValueWithVcsException<List<BeforeAfter<DiffContent>>> value) {
      final List<BeforeAfter<DiffContent>> contents;
      try {
        contents = value.get();
        if (contents == null) throw new VcsException("Can not load content");
      }
      catch (VcsException e) {
        return new Pair<JPanel, Disposable>(UIVcsUtil.errorPanel(e.getMessage(), true), null);
      }
      if (contents.isEmpty()) return noDifferences();
      assert contents.size() == 1;
      final DiffPanel panel = myDiffPanelHolder.getOrCreate();
      panel.setContents(contents.get(0).getBefore(), contents.get(0).getAfter());
      ((DiffPanelImpl) panel).getOptions().setRequestFocusOnNewContent(false);

      final JPanel wholeWrapper = new JPanel(new BorderLayout());
      final JPanel topPanel = new JPanel(new BorderLayout());
      final JPanel wrapper = new JPanel();
      final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
      wrapper.setLayout(boxLayout);
      final JLabel label = new JLabel(changeDescription(change));
      label.setBorder(BorderFactory.createEmptyBorder(1,2,0,0));
      wrapper.add(label);
      topPanel.add(wrapper, BorderLayout.CENTER);

      wholeWrapper.add(topPanel, BorderLayout.NORTH);
      wholeWrapper.add(new JBScrollPane(panel.getComponent()), BorderLayout.CENTER);

      return new Pair<JPanel, Disposable>(wholeWrapper, new Disposable() {
        @Override
        public void dispose() {
          myDiffPanelHolder.resetPanels();
        }
      });
    }

    @Override
    public ValueWithVcsException<List<BeforeAfter<DiffContent>>> load(final Change change) {
      return new ValueWithVcsException<List<BeforeAfter<DiffContent>>>() {
        @Override
        protected List<BeforeAfter<DiffContent>> computeImpl() throws VcsException {
          return myRequestFromChange.createRequestForChange(change, 0);
        }
      };
    }
  }

  private abstract static class ValueWithVcsException<T> extends TransparentlyFailedValue<T, VcsException> {
    protected ValueWithVcsException() {
      try {
        set(computeImpl());
      }
      catch (VcsException e) {
        fail(e);
      }
    }

    protected abstract T computeImpl() throws VcsException;
  }

  private static Pair<JPanel, Disposable> noDifferences() {
    return new Pair<JPanel, Disposable>(
      UIVcsUtil.errorPanel(DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text"), false), null);
  }

  private static class FragmentedDiffDetailsProvider implements VcsChangeDetailsProvider<ValueWithVcsException<List<BeforeAfter<ShiftedSimpleContent>>>> {
    private final DiffRequestFromChange<ShiftedSimpleContent> myRequestFromChange;
    private final Project myProject;
    private final LinkedList<DiffPanel> myDiffPanelCache;

    private FragmentedDiffDetailsProvider(Project project,
                                          DiffRequestFromChange<ShiftedSimpleContent> requestFromChange,
                                          final LinkedList<DiffPanel> diffPanelCache) {
      myRequestFromChange = requestFromChange;
      myProject = project;
      myDiffPanelCache = diffPanelCache;
    }

    @Override
    public String getProgressTitle() {
      return "Loading change content";
    }

    @Override
    public boolean canComment(Change change) {
      return myRequestFromChange.canCreateRequest(change);
    }

    @Override
    public ValueWithVcsException<List<BeforeAfter<ShiftedSimpleContent>>> load(final Change change) {
      return new ValueWithVcsException<List<BeforeAfter<ShiftedSimpleContent>>>() {
        @Override
        protected List<BeforeAfter<ShiftedSimpleContent>> computeImpl() throws VcsException {
          return myRequestFromChange.createRequestForChange(change, extraLines);
        }
      };
    }

    @Override
    public Pair<JPanel, Disposable> comment(Change change, ValueWithVcsException<List<BeforeAfter<ShiftedSimpleContent>>> value) {
      final List<BeforeAfter<ShiftedSimpleContent>> requestForChange;
      try {
        requestForChange = value.get();
        if (requestForChange == null) throw new VcsException("Can not load content");
        if (requestForChange.isEmpty()) {
          return noDifferences();
        }
      }
      catch (VcsException e) {
        return new Pair<JPanel, Disposable>(UIVcsUtil.errorPanel(e.getMessage(), true), null);
      }

      final ChangesFragmentedDiffPanel panel =
        new ChangesFragmentedDiffPanel(myProject, requestForChange, myDiffPanelCache, changeDescription(change));
      panel.buildUi();
      return new Pair<JPanel, Disposable>(panel.getPanel(), panel);
    }
  }

  private static String changeDescription(Change o) {
    return new StringBuilder().append(ChangesUtil.getFilePath(o).getName()).append(" (").append(
      o.getBeforeRevision() == null
      ? "New" : o.getBeforeRevision().getRevisionNumber().asString()).append(")").toString();
  }
}
