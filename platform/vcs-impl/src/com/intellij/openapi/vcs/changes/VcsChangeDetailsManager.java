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
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.actions.DiffRequestFromChange;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
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
  private final Map<VcsKey, Convertor<Change, Pair<JPanel, Disposable>>> myProviderMap = new HashMap<VcsKey, Convertor<Change, Pair<JPanel, Disposable>>>();
  private final List<Convertor<Change, Pair<JPanel, Disposable>>> myDedicatedList;
  // todo also check for size
  private final LinkedList<DiffPanel> myDiffPanelCache;

  public VcsChangeDetailsManager(final Project project) {
    myDedicatedList = new ArrayList<Convertor<Change,Pair<JPanel, Disposable>>>();

    myDiffPanelCache = new LinkedList<DiffPanel>();
    myDedicatedList.add(new BinaryDiffDetailsProvider(project, myDiffPanelCache));
    myDedicatedList.add(new FragmentedDiffDetailsProvider(project, new FragmentedDiffRequestFromChange(project), myDiffPanelCache));

    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        for (DiffPanel diffPanel : myDiffPanelCache) {
          Disposer.dispose(diffPanel);
        }
      }
    });
  }

  @Nullable
  public Pair<JPanel, Disposable> getPanel(final Change change) {
    // text details
    for (Convertor<Change, Pair<JPanel, Disposable>> convertor : myDedicatedList) {
      final Pair<JPanel, Disposable> pair = convertor.convert(change);
      if (pair != null) {
        return pair;
      }
    }
    return null;
  }

  private static class BinaryDiffDetailsProvider implements Convertor<Change,Pair<JPanel, Disposable>> {
    private final BinaryDiffRequestFromChange myRequestFromChange;
    private final Project myProject;
    private DiffPanelHolder myDiffPanelHolder;

    private BinaryDiffDetailsProvider(Project project, LinkedList<DiffPanel> diffPanelCache) {
      myProject = project;
      myRequestFromChange = new BinaryDiffRequestFromChange(myProject);
      myDiffPanelHolder = new DiffPanelHolder(diffPanelCache, project);
    }

    @Override
    public Pair<JPanel, Disposable> convert(Change o) {
      final List<BeforeAfter<DiffContent>> contents;
      try {
        contents = myRequestFromChange.createRequestForChange(o, 0);
      }
      catch (VcsException e) {
        return new Pair<JPanel, Disposable>(errorPanel(e.getMessage(), true), null);
      }
      if (contents == null || contents.isEmpty()) return null;
      assert contents.size() == 1;
      final DiffPanel panel = myDiffPanelHolder.getOrCreate();
      panel.setContents(contents.get(0).getBefore(), contents.get(0).getAfter());
      ((DiffPanelImpl) panel).getOptions().setRequestFocusOnNewContent(false);

      final JPanel wholeWrapper = new JPanel(new BorderLayout());
      final JPanel topPanel = new JPanel(new BorderLayout());
      final JPanel wrapper = new JPanel();
      final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
      wrapper.setLayout(boxLayout);
      final JLabel label = new JLabel(changeDescription(o));
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
  }

  private static class FragmentedDiffDetailsProvider implements Convertor<Change,Pair<JPanel, Disposable>> {
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
    public Pair<JPanel, Disposable> convert(Change o) {
      try {
        final List<BeforeAfter<ShiftedSimpleContent>> requestForChange = myRequestFromChange.createRequestForChange(o, extraLines);
        if (requestForChange == null || requestForChange.isEmpty()) return null;
        final ChangesFragmentedDiffPanel panel =
          new ChangesFragmentedDiffPanel(myProject, requestForChange, myDiffPanelCache, changeDescription(o));
        return new Pair<JPanel, Disposable>(panel.getPanel(), panel);
      }
      catch (VcsException e) {
        return new Pair<JPanel, Disposable>(errorPanel(e.getMessage(), true), null);
      }
    }

  }

  private static String changeDescription(Change o) {
    return new StringBuilder().append(ChangesUtil.getFilePath(o).getName()).append(" (").append(
      o.getBeforeRevision() == null
      ? "New" : o.getBeforeRevision().getRevisionNumber().asString()).append(")").toString();
  }

  public static JPanel errorPanel(final String text, boolean isError) {
    final JLabel label = new JLabel(text);
    label.setForeground(isError ? SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor() : UIUtil.getInactiveTextColor());
    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(label, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                         new Insets(1,1,1,1), 0,0));
    return wrapper;
  }
}
