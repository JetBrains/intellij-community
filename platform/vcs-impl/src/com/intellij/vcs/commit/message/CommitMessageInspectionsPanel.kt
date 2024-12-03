// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

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
import com.intellij.openapi.util.Disposer;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;

import static com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel.areToolDescriptorsChanged;
import static com.intellij.util.containers.ContainerUtil.exists;

@ApiStatus.Internal
public class CommitMessageInspectionsPanel extends BorderLayoutPanel implements Disposable, UnnamedConfigurable {
  @NotNull private final Project myProject;
  @NotNull private final List<ToolDescriptors> myInitialToolDescriptors = new ArrayList<>();
  @NotNull private final Map<HighlightDisplayKey, CommitMessageInspectionDetails> myToolDetails = new HashMap<>();
  @NotNull private final InspectionConfigTreeNode myRoot = new InspectionConfigTreeNode.Group("");
  private InspectionProfileModifiableModel myModifiableModel;
  private final InspectionsConfigTreeTable myInspectionsTable;
  private final Wrapper myDetailsPanel = new Wrapper() {
    @Override
    public boolean isNull() {
      // make inspections table not to occupy all available width if there is no current details
      return false;
    }
  };
  private CommitMessageInspectionDetails myCurrentDetails;

  public CommitMessageInspectionsPanel(@NotNull Project project) {
    myProject = project;

    myModifiableModel = createProfileModel();
    myInspectionsTable = createInspectionsTable();

    JBSplitter splitter = new JBSplitter("CommitMessageInspectionsPanelSplitter", 0.5f);
    splitter.setShowDividerIcon(false);
    splitter.setFirstComponent(myInspectionsTable);
    splitter.setSecondComponent(myDetailsPanel);
    addToCenter(splitter);
    setPreferredSize(JBUI.size(650, 120));
  }

  @NotNull
  private InspectionProfileModifiableModel createProfileModel() {
    CommitMessageInspectionProfile profile = CommitMessageInspectionProfile.getInstance(myProject);
    return new InspectionProfileModifiableModel(profile);
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

  @NotNull
  private InspectionsConfigTreeTable createInspectionsTable() {
    InspectionsConfigTreeTable table = InspectionsConfigTreeTable.create(new MyInspectionsTableSettings(myRoot, myProject), this);
    table.setRootVisible(false);
    table.setTreeCellRenderer(new MyInspectionsTreeRenderer());
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    table.getTree().addTreeSelectionListener(e -> updateDetailsPanel());
    table.setBorder(IdeBorderFactory.createBorder());
    return table;
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
    clearToolDetails();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public void reset() {
    clearToolDetails();
    myModifiableModel.getAllTools().forEach(ScopeToolState::resetConfigPanel);
    myModifiableModel = createProfileModel();
    initToolDescriptors();

    TreeState state = TreeState.createOn(myInspectionsTable.getTree(), myRoot);
    buildInspectionsModel();
    ((DefaultTreeModel)myInspectionsTable.getTree().getModel()).reload();
    state.applyTo(myInspectionsTable.getTree(), myRoot);
  }

  private void clearToolDetails() {
    for (CommitMessageInspectionDetails details : myToolDetails.values()) {
      Disposer.dispose(details);
    }
    myToolDetails.clear();
    setDetails(null);
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
    myProject.getMessageBus().syncPublisher(CommitMessageInspectionProfile.TOPIC).profileChanged();
    reset();
  }

  private class MyInspectionsTableSettings extends InspectionsConfigTreeTable.InspectionsConfigTreeTableSettings {
    MyInspectionsTableSettings(@NotNull InspectionConfigTreeNode root, @NotNull Project project) {
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
    MyInspectionTreeNode(@NotNull ToolDescriptors descriptors) {
      super(() -> descriptors);
    }

    @NotNull
    @Override
    public Descriptor getDefaultDescriptor() {
      return Objects.requireNonNull(super.getDefaultDescriptor());
    }

    @NotNull
    @Override
    public ToolDescriptors getDescriptors() {
      return Objects.requireNonNull(super.getDescriptors());
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
