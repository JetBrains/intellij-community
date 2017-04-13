/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.commit;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeRenderer;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.Panels;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

public class CommitMessageInspectionsPanel extends BorderLayoutPanel implements Disposable, UnnamedConfigurable {
  @NotNull private final Project myProject;
  @NotNull private final CommitMessageInspectionProfile myProfile;
  @NotNull private final List<ToolDescriptors> myInitialToolDescriptors = newArrayList();
  @NotNull private final InspectionConfigTreeNode myRoot = new InspectionConfigTreeNode.Group("");
  private InspectionProfileModifiableModel myModifiableModel;
  private InspectionsConfigTreeTable myInspectionsTable;

  public CommitMessageInspectionsPanel(@NotNull Project project) {
    myProject = project;
    myProfile = CommitMessageInspectionProfile.getInstance(myProject);

    resetProfileModel();
    init();
    setPreferredSize(JBUI.size(650, 120));
  }

  private void resetProfileModel() {
    myModifiableModel = new InspectionProfileModifiableModel(myProfile);
  }

  private void initToolDescriptors() {
    myInitialToolDescriptors.clear();
    for (ScopeToolState state : myModifiableModel.getDefaultStates(myProject)) {
      myInitialToolDescriptors.add(ToolDescriptors.fromScopeToolState(state, myModifiableModel, myProject));
    }
  }

  private void buildInspectionsModel() {
    myRoot.removeAllChildren();
    for (ToolDescriptors toolDescriptors : myInitialToolDescriptors) {
      myRoot.add(new MyInspectionTreeNode(toolDescriptors));
    }
    TreeUtil.sortRecursively(myRoot, new InspectionsConfigTreeComparator());
  }

  private void init() {
    myInspectionsTable = InspectionsConfigTreeTable.create(new MyInspectionsTableSettings(myProject, myRoot), this);
    myInspectionsTable.setRootVisible(false);
    myInspectionsTable.setTreeCellRenderer(new MyInspectionsTreeRenderer());
    myInspectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myInspectionsTable.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    JBSplitter splitter = new JBSplitter("CommitMessageInspectionsPanelSplitter", 0.5f);
    splitter.setShowDividerIcon(false);
    splitter.setFirstComponent(myInspectionsTable);
    splitter.setSecondComponent(Panels.simplePanel());
    addToCenter(splitter);
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public void reset() {
    resetProfileModel();
    initToolDescriptors();
    buildInspectionsModel();
    ((DefaultTreeModel)myInspectionsTable.getTree().getModel()).reload();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  private class MyInspectionsTableSettings extends InspectionsConfigTreeTable.InspectionsConfigTreeTableSettings {
    public MyInspectionsTableSettings(@NotNull Project project, @NotNull InspectionConfigTreeNode root) {
      super(root, project);
    }

    @NotNull
    @Override
    protected InspectionProfileImpl getInspectionProfile() {
      return myModifiableModel;
    }

    @Override
    protected void onChanged(@NotNull InspectionConfigTreeNode node) {
    }

    @Override
    public void updateRightPanel() {
    }
  }

  private static class MyInspectionTreeNode extends InspectionConfigTreeNode {
    public MyInspectionTreeNode(@NotNull ToolDescriptors descriptors) {
      setUserObject(descriptors);
    }
  }

  private static class MyInspectionsTreeRenderer extends InspectionsConfigTreeRenderer {
    @Nullable
    @Override
    protected String getFilter() {
      return null;
    }
  }
}
