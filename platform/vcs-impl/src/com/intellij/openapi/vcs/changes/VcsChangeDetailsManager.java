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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.HashMap;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * todo extract interface and create stub for dummy project
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 5:36 PM
 */
public class VcsChangeDetailsManager {
  private final Map<VcsKey, VcsChangeDetailsProvider> myProviderMap = new HashMap<VcsKey, VcsChangeDetailsProvider>();
  private final List<VcsChangeDetailsProvider> myDedicatedList;
  private final Project myProject;
  private final BackgroundTaskQueue myQueue;

  public VcsChangeDetailsManager(final Project project) {
    myProject = project;
    myQueue = new BackgroundTaskQueue(myProject, "Loading change details");
    myDedicatedList = new ArrayList<VcsChangeDetailsProvider>();

    myDedicatedList.add(new BinaryDetailsProviderNew(project, myQueue));
    myDedicatedList.add(new FragmentedDiffDetailsProvider(myProject, myQueue));

    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myQueue.clear();
      }
    });
  }

  public boolean canComment(final Change change) {
    for (VcsChangeDetailsProvider provider : myDedicatedList) {
      if (provider.canComment(change)) return true;
    }
    return false;
  }

  @Nullable
  public RefreshablePanel getPanel(final Change change, JComponent parent) {
    for (VcsChangeDetailsProvider convertor : myDedicatedList) {
      if (! convertor.canComment(change)) continue;
      RefreshablePanel panel = convertor.comment(change, parent);
      if (panel != null) {
        return panel;
      }
    }
    return null;
  }

  public static VcsChangeDetailsManager getInstance(Project project) {
    return ServiceManager.getService(project, VcsChangeDetailsManager.class);
  }

  private static class BinaryDetailsProviderNew implements VcsChangeDetailsProvider {
    private final Project myProject;
    private final BackgroundTaskQueue myQueue;

    private BinaryDetailsProviderNew(Project project, final BackgroundTaskQueue queue) {
      myProject = project;
      myQueue = queue;
    }

    @Override
    public boolean canComment(Change change) {
      FilePath path = ChangesUtil.getFilePath(change);
      if (path != null && path.isDirectory()) return false;
      return ShowDiffAction.isBinaryChangeAndCanShow(myProject, change);
    }

    @Override
    public RefreshablePanel comment(Change change, JComponent parent) {
      return new BinaryDiffDetailsPanel(myProject, myQueue, change);
    }
  }

  private static class BinaryDiffDetailsPanel extends AbstractRefreshablePanel<ValueWithVcsException<List<BeforeAfter<DiffContent>>>> {
    private final BinaryDiffRequestFromChange myRequestFromChange;
    private final Project myProject;
    private final ChangeListManager myChangeListManager;
    private final FilePath myFilePath;
    private Change myChange;
    private final DiffPanel myPanel;

    private BinaryDiffDetailsPanel(Project project, BackgroundTaskQueue queue, final Change change) {
      super(project, "Loading change content", queue);
      myProject = project;
      myFilePath = ChangesUtil.getFilePath(change);
      myRequestFromChange = new BinaryDiffRequestFromChange(myProject);
      myChangeListManager = ChangeListManager.getInstance(myProject);

      myPanel = DiffManager.getInstance().createDiffPanel(null, myProject,this);
      myPanel.enableToolbar(false);
      myPanel.removeStatusBar();
      DiffPanelOptions o = ((DiffPanelEx)myPanel).getOptions();
      o.setRequestFocusOnNewContent(false);
    }

    @Override
    protected void refreshPresentation() {
    }

    @Override
    protected ValueWithVcsException<List<BeforeAfter<DiffContent>>> loadImpl() throws VcsException {
      myChange = myChangeListManager.getChange(myFilePath);
      if (myChange == null) {
        return null;
      }
      return new ValueWithVcsException<List<BeforeAfter<DiffContent>>>() {
        @Override
        protected List<BeforeAfter<DiffContent>> computeImpl() throws VcsException {
          return myRequestFromChange.createRequestForChange(myChange, 0);
        }
      };
    }

    @Override
    protected JPanel dataToPresentation(ValueWithVcsException<List<BeforeAfter<DiffContent>>> value) {
      if (value == null) return noDifferences();
      final List<BeforeAfter<DiffContent>> contents;
      try {
        contents = value.get();
        if (contents == null) throw new VcsException("Can not load content");
      }
      catch (VcsException e) {
        return UIVcsUtil.errorPanel(e.getMessage(), true);
      }
      if (contents.isEmpty()) return noDifferences();
      assert contents.size() == 1;

      myPanel.setContents(contents.get(0).getBefore(), contents.get(0).getAfter());
      ((DiffPanelImpl)myPanel).getOptions().setRequestFocusOnNewContent(false);

      final JPanel wholeWrapper = new JPanel(new BorderLayout());
      final JPanel topPanel = new JPanel(new BorderLayout());
      final JPanel wrapper = new JPanel();
      final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
      wrapper.setLayout(boxLayout);
      final JLabel label = new JLabel(changeDescription(myChange));
      label.setBorder(BorderFactory.createEmptyBorder(1,2,0,0));
      wrapper.add(label);
      topPanel.add(wrapper, BorderLayout.CENTER);

      wholeWrapper.add(topPanel, BorderLayout.NORTH);
      //wholeWrapper.add(new JBScrollPane(panel.getComponent()), BorderLayout.CENTER);
      wholeWrapper.add(myPanel.getComponent(), BorderLayout.CENTER);
      return wholeWrapper;
    }

    @Override
    protected void disposeImpl() {
    }

    @Override
    public void away() {
      //
    }
  }

  private static JPanel noDifferences() {
    return UIVcsUtil.errorPanel(DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text"), false);
  }

  private static class FragmentedDiffDetailsProvider implements VcsChangeDetailsProvider {
    private final Project myProject;
    private final BackgroundTaskQueue myQueue;

    private FragmentedDiffDetailsProvider(Project project, final BackgroundTaskQueue queue) {
      myProject = project;
      myQueue = queue;
    }

    @Override
    public boolean canComment(Change change) {
      return FragmentedDiffRequestFromChange.canCreateRequest(change);
    }

    @Override
    public RefreshablePanel comment(Change change, JComponent parent) {
      return new FragmentedDiffDetailsPanel(myProject, myQueue, change, parent);
    }
  }

  private static class FragmentedDiffDetailsPanel extends AbstractRefreshablePanel<ValueWithVcsException<PreparedFragmentedContent>> {
    private final FragmentedDiffRequestFromChange myRequestFromChange;
    private final FilePath myFilePath;
    private final ChangeListManager myChangeListManager;
    private final ChangesFragmentedDiffPanel myDiffPanel;
    private final Project myProject;

    private FragmentedDiffDetailsPanel(Project project, BackgroundTaskQueue queue, final Change change, JComponent parent) {
      super(project, "Loading change content", queue);
      myProject = project;
      myFilePath = ChangesUtil.getFilePath(change);
      myRequestFromChange = new FragmentedDiffRequestFromChange(project);
      myChangeListManager = ChangeListManager.getInstance(project);
      myDiffPanel = new ChangesFragmentedDiffPanel(project, changeDescription(change), parent);
      myDiffPanel.buildUi();
    }

    @Override
    protected void refreshPresentation() {
      myDiffPanel.refreshPresentation();
    }

    @Override
    protected ValueWithVcsException<PreparedFragmentedContent> loadImpl() throws VcsException {
      return new ValueWithVcsException<PreparedFragmentedContent>() {
        @Override
        protected PreparedFragmentedContent computeImpl() throws VcsException {
          final Change change = myChangeListManager.getChange(myFilePath);
          if (change == null) {
            return null;
          }
          myDiffPanel.setTitle(changeDescription(change));
          return myRequestFromChange.getRanges(change);
        }
      };
    }

    @Override
    protected JPanel dataToPresentation(ValueWithVcsException<PreparedFragmentedContent> value) {
      final PreparedFragmentedContent requestForChange;
      try {
        requestForChange = value.get();
        if (requestForChange == null) return noDifferences();
        if (requestForChange.isEmpty()) {
          return noDifferences();
        }
      }
      catch (VcsException e) {
        return UIVcsUtil.errorPanel(e.getMessage(), true);
      }
      myDiffPanel.refreshData(requestForChange);
      return myDiffPanel.getPanel();
    }

    @Override
    protected void disposeImpl() {
      Disposer.dispose(myDiffPanel);
    }

    @Override
    public boolean refreshDataSynch() {
      try {
        myTicket.increment();
        ValueWithVcsException<PreparedFragmentedContent> exception = loadImpl();
        dataToPresentation(exception);
      }
      catch (VcsException e) {
        return false;
      }
      return true;
    }

    @Override
    public void away() {
      myDiffPanel.away();
    }
  }

  private static String changeDescription(Change o) {
    return new StringBuilder().append(ChangesUtil.getFilePath(o).getName()).append(" (").append(
      o.getBeforeRevision() == null
      ? "New" : beforeRevisionText(o)).append(")").toString();
  }

  private static String beforeRevisionText(Change o) {
    VcsRevisionNumber revisionNumber = o.getBeforeRevision().getRevisionNumber();
    return revisionNumber instanceof ShortVcsRevisionNumber ? ((ShortVcsRevisionNumber) revisionNumber).toShortString() :
           revisionNumber.asString();
  }
}
