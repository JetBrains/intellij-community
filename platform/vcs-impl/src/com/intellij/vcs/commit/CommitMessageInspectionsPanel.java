// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeRenderer;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;
import java.util.Map;

import static com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel.areToolDescriptorsChanged;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;

public class CommitMessageInspectionsPanel extends BorderLayoutPanel implements Disposable, UnnamedConfigurable {
  @NotNull private final Project myProject;
  @NotNull private final CommitMessageInspectionProfile myProfile;
  @NotNull private final List<ToolDescriptors> myInitialToolDescriptors = newArrayList();
  @NotNull private final Map<HighlightDisplayKey, CommitMessageInspectionDetails> myToolDetails = newHashMap();
  @NotNull private final InspectionConfigTreeNode myRoot = new InspectionConfigTreeNode.Group("");
  private InspectionProfileModifiableModel myModifiableModel;
  private InspectionsConfigTreeTable myInspectionsTable;
  private Wrapper myDetailsPanel = new Wrapper() {
    @Override
    public boolean isNull() {
      // make inspections table not to occupy all available width if there is no current details
      return false;
    }
  };
  private CommitMessageInspectionDetails myCurrentDetails;

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
    TreeUtil.sortRecursively(myRoot, InspectionsConfigTreeComparator.INSTANCE);
  }

  private void init() {
    myInspectionsTable = InspectionsConfigTreeTable.create(new MyInspectionsTableSettings(myProject, myRoot), this);
    myInspectionsTable.setRootVisible(false);
    myInspectionsTable.setTreeCellRenderer(new MyInspectionsTreeRenderer());
    myInspectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myInspectionsTable.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myInspectionsTable.getTree().addTreeSelectionListener(e -> updateDetailsPanel());
    myInspectionsTable.setBorder(IdeBorderFactory.createBorder());

    JBSplitter splitter = new JBSplitter("CommitMessageInspectionsPanelSplitter", 0.5f);
    splitter.setShowDividerIcon(false);
    splitter.setFirstComponent(myInspectionsTable);
    splitter.setSecondComponent(myDetailsPanel);
    addToCenter(splitter);
  }

  private void updateDetailsPanel() {
    MyInspectionTreeNode node = getSelectedNode();

    if (node == null && myCurrentDetails != null ||
        node != null && (myCurrentDetails == null || node.getKey() != myCurrentDetails.getKey())) {
      setDetails(node);
    }

    if (myCurrentDetails != null) {
      myCurrentDetails.update();
    }
  }

  private void setDetails(@Nullable MyInspectionTreeNode node) {
    myCurrentDetails = node != null ? myToolDetails.computeIfAbsent(node.getKey(), key -> createDetails(node)) : null;

    myDetailsPanel.setContent(myCurrentDetails != null ? myCurrentDetails.createComponent() : null);
    myDetailsPanel.repaint();
  }

  @NotNull
  private CommitMessageInspectionDetails createDetails(@NotNull MyInspectionTreeNode node) {
    CommitMessageInspectionDetails details = new CommitMessageInspectionDetails(myProject, myModifiableModel, node.getDefaultDescriptor());
    details.addListener(severity -> myInspectionsTable.updateUI());
    return details;
  }

  @Nullable
  private MyInspectionTreeNode getSelectedNode() {
    TreePath selectedPath = myInspectionsTable.getTree().getPathForRow(myInspectionsTable.getSelectedRow());

    return selectedPath != null ? (MyInspectionTreeNode)selectedPath.getLastPathComponent() : null;
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
    myToolDetails.clear();
    myModifiableModel.getAllTools().forEach(ScopeToolState::resetConfigPanel);
    resetProfileModel();
    initToolDescriptors();

    TreeState state = TreeState.createOn(myInspectionsTable.getTree(), myRoot);
    buildInspectionsModel();
    ((DefaultTreeModel)myInspectionsTable.getTree().getModel()).reload();
    state.applyTo(myInspectionsTable.getTree(), myRoot);
  }

  @Override
  public boolean isModified() {
    return exists(myInitialToolDescriptors, toolDescriptors -> areToolDescriptorsChanged(myProject, myModifiableModel, toolDescriptors)) ||
           exists(myToolDetails.values(), CommitMessageInspectionDetails::isModified);
  }

  @Override
  public void apply() throws ConfigurationException {
    for (CommitMessageInspectionDetails details : myToolDetails.values()) {
      details.apply();
    }
    myModifiableModel.commit();
    reset();
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
      updateDetailsPanel();
    }
  }

  private static class MyInspectionTreeNode extends InspectionConfigTreeNode.Tool {
    public MyInspectionTreeNode(@NotNull ToolDescriptors descriptors) {
      super(() -> descriptors);
    }

    @NotNull
    @Override
    public Descriptor getDefaultDescriptor() {
      return notNull(super.getDefaultDescriptor());
    }

    @NotNull
    @Override
    public ToolDescriptors getDescriptors() {
      return notNull(super.getDescriptors());
    }

    @Override
    protected boolean calculateIsProperSettings() {
      return false;
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
