/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This dialog shows files which have line separators different than in the project code style and allows to select files to be converted.
 * The dialog is shown before commit or before update.
 * In the settings the user may choose {@link GitVcsSettings.ConversionPolicy} and don't show the dialog at all.
 */
public class GitConvertFilesDialog extends DialogWrapper {
  public static final int DO_NOT_CONVERT = NEXT_USER_EXIT_CODE; // "Do not convert" button code
  private JCheckBox myDoNotShowCheckBox; // don't show the dialog again
  private JPanel myRootPanel;
  private CheckboxTreeBase myFilesToConvert; // Tree of files selected to convert
  private CheckedTreeNode myRootNode;
  private final Project myProject;

  public GitConvertFilesDialog(Project project, Map<VirtualFile, Set<VirtualFile>> filesToShow) {
    super(project, true);
    myProject = project;
    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>(filesToShow.keySet());
    Collections.sort(roots, GitUtil.VIRTUAL_FILE_COMPARATOR);
    for (VirtualFile root : roots) {
      CheckedTreeNode vcsRoot = new CheckedTreeNode(root);
      myRootNode.add(vcsRoot);
      ArrayList<VirtualFile> files = new ArrayList<VirtualFile>(filesToShow.get(root));
      Collections.sort(files, GitUtil.VIRTUAL_FILE_COMPARATOR);
      for (VirtualFile file : files) {
        vcsRoot.add(new CheckedTreeNode(file));
      }
    }
    TreeUtil.expandAll(myFilesToConvert);
    setTitle(GitBundle.getString("crlf.convert.title"));
    setOKButtonText(GitBundle.getString("crlf.convert.convert"));
    init();
  }

  @Override
  public void show() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      DialogManager.getInstance(myProject).showDialog(this);
    } else {
      super.show();
    }
  }

  /**
   * @return did user selected the checkbox "don't show this dialog again".
   */
  public boolean isDontShowAgainChosen() {
    return myDoNotShowCheckBox.isSelected();
  }

  /**
   * @return Files selected to be converted.
   */
  public VirtualFile[] getSelectedFiles() {
    return myFilesToConvert.getCheckedNodes(VirtualFile.class, null);
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DoNotConvertAction(), getCancelAction()};
  }

  private void createUIComponents() {
    myRootNode = new CheckedTreeNode("ROOT");
    myFilesToConvert = new CheckboxTree(new FileTreeCellRenderer(), myRootNode) {
      protected void onNodeStateChanged(CheckedTreeNode node) {
        VirtualFile[] files = myFilesToConvert.getCheckedNodes(VirtualFile.class, null);
        setOKActionEnabled(files != null && files.length > 0);
        super.onNodeStateChanged(node);
      }
    };
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * Action used to indicate that no conversion should be performed
   */
  class DoNotConvertAction extends AbstractAction {
    private static final long serialVersionUID = 1931383640152023206L;

    DoNotConvertAction() {
      putValue(NAME, GitBundle.getString("crlf.convert.leave"));
      putValue(DEFAULT_ACTION, Boolean.FALSE);
    }

    public void actionPerformed(ActionEvent e) {
      if (myPerformAction) return;
      try {
        myPerformAction = true;
        close(DO_NOT_CONVERT);
      }
      finally {
        myPerformAction = false;
      }
    }
  }


  /**
   * The cell renderer for the tree
   */
  static class FileTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      // Fix GTK background
      if (UIUtil.isUnderGTKLookAndFeel()){
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
      ColoredTreeCellRenderer r = getTextRenderer();
      if (!(value instanceof CheckedTreeNode)) {
        // unknown node type
        renderUnknown(r, value);
        return;
      }
      CheckedTreeNode node = (CheckedTreeNode)value;
      if (!(node.getUserObject() instanceof VirtualFile)) {
        // unknown node type
        renderUnknown(r, node.getUserObject());
        return;
      }
      VirtualFile file = (VirtualFile)node.getUserObject();
      if (leaf) {
        VirtualFile parent = (VirtualFile)((CheckedTreeNode)node.getParent()).getUserObject();
        // the real file
        Icon i = file.getIcon();
        if (i != null) {
          r.setIcon(i);
        }
        r.append(VcsFileUtil.getRelativeFilePath(file, parent), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      }
      else {
        // the vcs root node
        r.append(file.getPresentableUrl(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);
      }
    }

    /**
     * Render unknown node
     *
     * @param r     a renderer to use
     * @param value the unknown value
     */
    private static void renderUnknown(ColoredTreeCellRenderer r, Object value) {
      r.append("UNSUPPORTED NODE TYPE: " + (value == null ? "null" : value.getClass().getName()), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
