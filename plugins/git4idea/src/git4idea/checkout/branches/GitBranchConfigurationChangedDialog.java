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
package git4idea.checkout.branches;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The dialog shown to inform of changes in the current configuration
 */
public class GitBranchConfigurationChangedDialog extends DialogWrapper {
  /**
   * The new configuration exit code
   */
  private static final int NEW_CONFIGURATION = NEXT_USER_EXIT_CODE;
  /**
   * The name text field
   */
  private JTextField myNameTextField;
  /**
   * The table that describes configuration changes
   */
  private JBTable myTable;
  /**
   * The root panel
   */
  private JPanel myRootPanel;
  /**
   * The base directory for the project
   */
  private final File myBaseFile;
  /**
   * The branch descriptors to show in the table
   */
  private final List<BranchDescriptor> myBranches;
  /**
   * The new configuration action
   */
  private DialogWrapperExitAction myNewAction;


  /**
   * The constructor from project
   *
   * @param project  the project to use to display window
   * @param config
   * @param branches
   * @param names
   */
  protected GitBranchConfigurationChangedDialog(Project project,
                                                final GitBranchConfiguration config,
                                                List<BranchDescriptor> branches, final Set<String> names) {
    super(project, true);
    setTitle("Git Branch Configuration Changed");
    myBranches = branches;
    VirtualFile baseDir = project.getBaseDir();
    myBaseFile = baseDir == null ? null : new File(baseDir.getPath());
    myTable.setModel(new DescriptorTableModel());
    myNameTextField.setText(config.getName());
    myNewAction = new DialogWrapperExitAction("New Configuration", NEW_CONFIGURATION);
    final DocumentAdapter l = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String text = myNameTextField.getText().trim();
        if (text.length() == 0) {
          setError("Empty configuration name is not allowed.");
        }
        else if (text.equals(config.getName())) {
          setError(null);
          myNewAction.setEnabled(false);
        }
        else if (names.contains(text)) {
          setError("There is another configuration with the same name");
        }
        else {
          setError(null);
        }
      }

      private void setError(String s) {
        setErrorText(s);
        setOKActionEnabled(s == null);
        myNewAction.setEnabled(s == null);
      }
    };
    myNameTextField.getDocument().addDocumentListener(l);
    l.changedUpdate(null);
    setOKButtonText("Update");
    init();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  /**
   * Show the dialog in AWT thread and wait for its completion
   *
   * @param settings the settings to use
   * @param config   the configuration to check
   * @param roots    the roots collection
   * @return null if project is cancelled, or configuration that user has selected for storing the current state (created or updated)
   */
  @Nullable
  static GitBranchConfiguration showDialog(final GitBranchConfigurations settings,
                                           final GitBranchConfiguration config,
                                           final List<VirtualFile> roots)
    throws VcsException {
    final Set<String> names = settings.getConfigurationNames();
    final Ref<String> name = new Ref<String>();
    final Ref<Integer> code = new Ref<Integer>();
    final List<BranchDescriptor> list = prepareBranchDescriptors(settings, config, roots);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        GitBranchConfigurationChangedDialog d = new GitBranchConfigurationChangedDialog(settings.getProject(), config, list, names);
        d.show();
        code.set(d.getExitCode());
        name.set(d.myNameTextField.getText());
      }
    });
    if (code.get() == CANCEL_EXIT_CODE) {
      return null;
    }
    GitBranchConfiguration updateConfig;
    synchronized (settings.getStateLock()) {
      if (code.get() == OK_EXIT_CODE) {
        updateConfig = config;
        updateConfig.setName(name.get());
      }
      else if (code.get() == NEW_CONFIGURATION) {
        updateConfig = settings.createConfiguration(name.get());
      }
      else {
        throw new RuntimeException("Unexpected exit code: " + code.get());
      }
      updateConfig.clearReferences();
      for (BranchDescriptor d : list) {
        if (d.root != null) {
          updateConfig.setReference(d.root.getPath(), d.actualRef);
        }
      }
    }
    return updateConfig;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), myNewAction, getCancelAction()};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  static List<BranchDescriptor> prepareBranchDescriptors(GitBranchConfigurations settings,
                                                         GitBranchConfiguration config,
                                                         List<VirtualFile> roots)
    throws VcsException {
    List<BranchDescriptor> rc = new ArrayList<BranchDescriptor>();
    Map<String, String> map = config.getReferences();
    for (VirtualFile root : roots) {
      BranchDescriptor d = new BranchDescriptor();
      d.root = root;
      d.actualRef = settings.describeRoot(root);
      d.storedRef = map.remove(root.getPath());
      rc.add(d);
    }
    for (Map.Entry<String, String> m : map.entrySet()) {
      BranchDescriptor d = new BranchDescriptor();
      d.storedRoot = m.getKey();
      d.storedRef = m.getValue();
      rc.add(d);
    }
    return rc;
  }

  /**
   * The table model that describes roots
   */
  private class DescriptorTableModel extends AbstractTableModel {
    /**
     * The relative path for the root
     */
    private static final int ROOT_COLUMN = 0;
    /**
     * The actual reference
     */
    private static final int ACTUAL_REF_COLUMN = 1;
    /**
     * The reference stored in the configuration
     */
    private static final int STORED_REF_COLUMN = 2;


    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowCount() {
      return myBranches.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getColumnCount() {
      return STORED_REF_COLUMN + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      BranchDescriptor d = myBranches.get(rowIndex);
      switch (columnIndex) {
        case ROOT_COLUMN:
          return d.getRoot(myBaseFile);
        case ACTUAL_REF_COLUMN:
          return d.actualRef == null ? "" : d.actualRef;
        case STORED_REF_COLUMN:
          return d.storedRef == null ? "" : d.storedRef;
        default:
          throw new IllegalStateException("Unexpected column");
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(int column) {
      switch (column) {
        case ROOT_COLUMN:
          return "Vcs Root";
        case ACTUAL_REF_COLUMN:
          return "Actual";
        case STORED_REF_COLUMN:
          return "Configured";
        default:
          throw new IllegalStateException("Unexpected column");
      }
    }
  }


  /**
   * The descriptor for the branch
   */
  private static class BranchDescriptor {
    /**
     * The vcs root
     */
    VirtualFile root;
    /**
     * The branch information stored in the configuration
     */
    String storedRoot;
    /**
     * The stored reference
     */
    String storedRef;
    /**
     * The actual reference
     */
    String actualRef;

    public String getRoot(final File baseFile) {
      String path = root == null ? storedRoot : root.getPath();
      String relative = baseFile == null ? path : FileUtil.getRelativePath(baseFile, new File(path));
      return relative == null ? path : relative;
    }
  }
}
