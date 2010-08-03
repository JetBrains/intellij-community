/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

public class GitLogTreeFileSelector extends DialogWrapper {
  private final JPanel myPanel;
  private final Project myProject;
  private final VirtualFile myRoot;
  private FileSystemTree myFileSystemTree;

  public GitLogTreeFileSelector(final Project project, final VirtualFile root) {
    super(project, true);
    setTitle(root.getPath());
    myProject = project;
    myRoot = root;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setMinimumSize(new Dimension(400, 500));
    initUi();
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileSystemTree.getTree();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "git4idea.history.browser.GitLogTreeFileSelector";
  }

  private void initUi() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, true);
    descriptor.setRoot(myRoot);
    myFileSystemTree = FileSystemTreeFactory.SERVICE.getInstance().createFileSystemTree(myProject, descriptor);
    final JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(new GitLogTreeFileSelectorRenderer(myProject));
    myFileSystemTree.select(myRoot, null);

    myPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public VirtualFile[] getSelectedFiles() {
    return myFileSystemTree.getSelectedFiles();
  }
}
