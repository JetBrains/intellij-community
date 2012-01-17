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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.HashMap;
import git4idea.GitBranch;
import git4idea.GitDeprecatedRemote;
import git4idea.GitTag;
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import git4idea.i18n.GitBundle;
import git4idea.validators.GitBranchNameValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The component that allows specifying a list of references
 */
public class GitRefspecPanel extends JPanel {
  /**
   * The logger for the class
   */
  private static final Logger log = Logger.getInstance(Logger.class.getName());
  /**
   * Named remotes associated with the current git root
   */
  private final HashMap<String, GitDeprecatedRemote> myRemotes = new HashMap<String, GitDeprecatedRemote>();
  /**
   * The project
   */
  private Project myProject;
  /**
   * The git root for mapping
   */
  private VirtualFile myGitRoot;
  /**
   * Remote heads (for Add... dialog)
   */
  private final SortedSet<String> myRemoteHeads = new TreeSet<String>();
  /**
   * Remote tags (for Add.. dialog)
   */
  private final SortedSet<String> myRemoteTags = new TreeSet<String>();
  /**
   * The button that adds all branches button
   */
  private JButton myAddAllBranchesButton;
  /**
   * The button that adds selected references
   */
  private JButton myAddButton;
  /**
   * The button that removes currently selected entries from the table
   */
  private JButton myRemoveButton;
  /**
   * The text that contains remote name
   */
  private JTextField myRemoteNameTextField;
  /**
   * The root panel of the form
   */
  private JPanel myPanel;
  /**
   * The references table
   */
  private JTable myReferences;
  /**
   * The button that adds entry that maps all tags
   */
  private JButton myAddAllTagsButton;
  /**
   * Restore default mapping button
   */
  private JButton myDefaultButton;
  /**
   * The name of the remote
   */
  private String myRemote;
  /**
   * The source of default references
   */
  private ReferenceSource myReferenceSource;
  /**
   * Mapping table model
   */
  private final MyMappingTableModel myReferencesModel = new MyMappingTableModel();

  /**
   * A constructor
   */
  public GitRefspecPanel() {
    super(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.weighty = 1;
    c.fill = GridBagConstraints.BOTH;
    add(myPanel, c);
    setupTable();
    setupButtons();
  }


  /**
   * Validates fields
   *
   * @return null if there is no error; empty string means that there is no error yet but OK should be disabled; otherwise error text should be used as the current error for dialog
   */
  @Nullable
  public String validateFields() {
    final String remote = getRemoteName();
    if (remote.length() == 0) {
      if (myReferencesModel.isRemoteNameUsed()) {
        return GitBundle.getString("refspec.validation.remote.is.blank");
      }
    }
    else {
      if (!GitBranchNameValidator.INSTANCE.checkInput(remote)) {
        return GitBundle.getString("refspec.validation.remote.invalid");
      }
    }
    return null;
  }

  /**
   * Set project for panel
   *
   * @param project the context project
   */
  public void setProject(Project project) {
    myProject = project;
  }

  /**
   * Setup add/remove buttons
   */
  private void setupButtons() {
    // disable ok button if nothing is selected
    myReferences.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        myRemoveButton.setEnabled(myReferences.getSelectedRowCount() != 0);
      }
    });
    // remove selected mappings
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myReferencesModel.removeSelectedMapping();
      }
    });
    // add all tags (mapped to tags directory)
    myAddAllTagsButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myReferencesModel.addMapping(false, tagName("*"), tagName("*"));
      }
    });
    // all heads (mapped to remotes directory)
    myAddAllBranchesButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addAllBranches();
      }
    });
    // map selected tags and heads
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myGitRoot == null) {
          throw new IllegalStateException("Git root must be already set at this point.");
        }
        GitRefspecAddRefsDialog d = new GitRefspecAddRefsDialog(myProject, myGitRoot, myRemote, myRemoteTags, myRemoteHeads);
        d.show();
        if (!d.isOK()) {
          return;
        }
        for (String tag : d.getSelected(true)) {
          myReferencesModel.addMapping(false, tag, tag);
        }
        for (String head : d.getSelected(false)) {
          myReferencesModel.addMapping(true, head, remoteName(head.substring(GitBranch.REFS_HEADS_PREFIX.length())));
        }
      }
    });
    myDefaultButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String remote = myRemote;
        setRemote(null);
        setRemote(remote);
      }
    });
  }

  /**
   * Add mapping for all branches
   */
  private void addAllBranches() {
    myReferencesModel.addMapping(false, headName("*"), remoteName("*"));
  }


  /**
   * Generate tag with remote name
   *
   * @param remoteName the name of remote in the local system
   * @param tagName    the name of the tag
   * @return the full path to the head
   */
  private static String tagRemoteName(final String remoteName, final String tagName) {
    return GitTag.REFS_TAGS_PREFIX + remoteName + "/" + tagName;
  }

  /**
   * Simple tag name
   *
   * @param tagName the short name of tag
   * @return the fully qualified tag reference name
   */
  private static String tagName(final String tagName) {
    return GitTag.REFS_TAGS_PREFIX + tagName;
  }

  /**
   * Generate remote head name in local file system, note that as name of remote {@link #getRemoteName()} is used.
   *
   * @param headName the name head of remote in the local system
   * @return the full path to the head
   */
  private String remoteName(final String headName) {
    return remoteName(getRemoteName(), headName);
  }

  /**
   * Generate remote name in local file system
   *
   * @param remote   a remote name, if blank a local branch is returned.
   * @param headName the name head of remote in the local system
   * @return the full path to the head
   */
  private static String remoteName(final String remote, final String headName) {
    return remote.length() != 0 ? GitBranch.REFS_REMOTES_PREFIX + remote + "/" + headName : headName(headName);
  }

  /**
   * Generate head name
   *
   * @param head the head name
   * @return the full path to the head
   */
  private static String headName(final String head) {
    return GitBranch.REFS_HEADS_PREFIX + head;
  }

  /**
   * @return the current name of the remote
   */
  public String getRemoteName() {
    return myRemoteNameTextField.getText();
  }

  /**
   * Setup table header and table model
   */
  private void setupTable() {
    // setup model
    myReferences.setModel(myReferencesModel);
    myReferences.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myReferences.getColumnModel().getColumn(MyMappingTableModel.FORCE_COLUMN).sizeWidthToFit();
    myRemoteNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        myReferencesModel.remoteUpdated();
      }
    });
  }

  /**
   * Set git root for reference mapping
   *
   * @param gitRoot a git root
   */
  public void setGitRoot(final VirtualFile gitRoot) {
    if (gitRoot == myGitRoot) {
      return;
    }
    myGitRoot = gitRoot;
    myRemotes.clear();
    if (myGitRoot != null) {
      try {
        for (GitDeprecatedRemote r : GitDeprecatedRemote.list(myProject, myGitRoot)) {
          myRemotes.put(r.name(), r);
        }
      }
      catch (VcsException e) {
        GitUIUtil.showOperationError(myProject, e, "listing remotes");
      }
    }
  }

  /**
   * Set remote or url
   *
   * @param name a name of remote or URL
   */
  public void setRemote(String name) {
    if (name != null && name.length() == 0) {
      name = null;
    }
    if (myRemote == null && name == null || myRemote != null && myRemote.equals(name)) {
      return;
    }
    myRemote = name;
    myAddButton.setEnabled(myRemote != null && myRemote.length() != 0);
    final GitDeprecatedRemote remote = myRemotes.get(name);
    if (remote != null) {
      myRemoteNameTextField.setText(name);
      myRemoteNameTextField.setEditable(false);
      myDefaultButton.setEnabled(true);
    }
    else {
      myRemoteNameTextField.setText("");
      myRemoteNameTextField.setEditable(true);
      myDefaultButton.setEnabled(false);
    }
    myRemoteHeads.clear();
    myRemoteTags.clear();
    setDefaultMapping();
  }

  /**
   * Set default mapping
   */
  private void setDefaultMapping() {
    final GitDeprecatedRemote remote = myRemotes.get(myRemote);
    myReferencesModel.clear();
    if (remote != null && myReferenceSource == ReferenceSource.FETCH) {
      try {
        for (String ref : GitDeprecatedRemote.getFetchSpecs(myProject, myGitRoot, remote.name())) {
          StringScanner s = new StringScanner(ref);
          boolean force = s.tryConsume('+');
          String remotePart = s.boundedToken(':');
          String localPart = s.line();
          myReferencesModel.addMapping(force, remotePart, localPart);
        }
      }
      catch (VcsException e) {
        log.error("Failed to get fetch references ", e);
      }
    }
    else {
      addAllBranches();
    }
  }

  /**
   * Add listener that is fired when validation is required
   *
   * @param l a listener to add
   */
  public void addValidationRequiredListener(final ActionListener l) {
    myRemoteNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        //noinspection HardCodedStringLiteral
        l.actionPerformed(new ActionEvent(myRemoteNameTextField, ActionEvent.ACTION_PERFORMED, "validationRequired"));
      }
    });
  }

  /**
   * Set default reference source for panel.
   *
   * @param referenceSource a reference source
   */
  public void setReferenceSource(final ReferenceSource referenceSource) {
    myReferenceSource = referenceSource;
  }

  /**
   * @return references added to the model
   */
  public String[] getReferences() {
    return myReferencesModel.getReferences();
  }

  /**
   * Mapping table model
   */
  private class MyMappingTableModel extends AbstractTableModel {
    /**
     * Force column in the table
     */
    private static final int FORCE_COLUMN = 0;
    /**
     * Remote reference column in the table
     */
    private static final int REMOTE_COLUMN = 1;
    /**
     * Local reference column in the table
     */
    private static final int LOCAL_COLUMN = 2;
    /**
     * Remote name used for the table update
     */
    private String mySavedRemoteName = null;
    /**
     * The currently constructed mapping
     */
    private final ArrayList<RefMapping> myMapping = new ArrayList<RefMapping>();

    /**
     * {@inheritDoc}
     */
    public int getRowCount() {
      return myMapping.size();
    }

    /**
     * Remove currently selected mappings
     */
    public void removeSelectedMapping() {
      final int[] rows = myReferences.getSelectedRows();
      Arrays.sort(rows);
      for (int i = rows.length - 1; i >= 0; i--) {
        myMapping.remove(rows[i]);
      }
      fireTableDataChanged();
    }

    /**
     * {@inheritDoc}
     */
    public int getColumnCount() {
      return LOCAL_COLUMN + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      RefMapping m = myMapping.get(rowIndex);
      switch (columnIndex) {
        case FORCE_COLUMN:
          m.force = ((Boolean)aValue).booleanValue();
          break;
        case LOCAL_COLUMN:
          m.local = (String)aValue;
          break;
        case REMOTE_COLUMN:
          m.remote = (String)aValue;
          break;
        default:
          throw new IllegalStateException("Invalid column: " + columnIndex);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case FORCE_COLUMN:
          return GitBundle.getString("refspec.column.force");
        case LOCAL_COLUMN:
          return GitBundle.getString("refspec.column.local");
        case REMOTE_COLUMN:
          return GitBundle.getString("refspec.column.remote");
        default:
          throw new IllegalStateException("Invalid column: " + column);
      }
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      RefMapping m = myMapping.get(rowIndex);
      switch (columnIndex) {
        case FORCE_COLUMN:
          return m.force;
        case LOCAL_COLUMN:
          return m.local;
        case REMOTE_COLUMN:
          return m.remote;
        default:
          throw new IllegalStateException("Invalid column: " + columnIndex);
      }
    }

    /**
     * Add mapping
     *
     * @param force  a force flag
     * @param remote a remote reference
     * @param local  a local reference
     */
    public void addMapping(final boolean force, @NonNls final String remote, @NonNls final String local) {
      final RefMapping m = new RefMapping();
      m.force = force;
      m.remote = remote;
      m.local = local;
      int row = myMapping.size();
      myMapping.add(m);
      fireTableRowsInserted(row, row);
      if (mySavedRemoteName == null) {
        remoteUpdated();
      }
    }

    /**
     * This method updates all local heads in the table with remote name
     */
    private void remoteUpdated() {
      String newText = myRemoteNameTextField.getText();
      if (mySavedRemoteName != null && !newText.equals(mySavedRemoteName)) {
        @NonNls String oldTagsPrefix = tagRemoteName(mySavedRemoteName, "");
        @NonNls String newTagsPrefix = tagRemoteName(newText, "");
        @NonNls String oldHeadsPrefix = remoteName(mySavedRemoteName, "");
        @NonNls String newHeadsPrefix = remoteName(newText, "");
        for (RefMapping m : myMapping) {
          if (m.local.startsWith(oldTagsPrefix)) {
            m.local = newTagsPrefix + m.local.substring(oldTagsPrefix.length());
          }
          else if (m.local.startsWith(oldHeadsPrefix)) {
            m.local = newHeadsPrefix + m.local.substring(oldHeadsPrefix.length());
          }
        }
        fireTableDataChanged();
      }
      mySavedRemoteName = newText;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (columnIndex == FORCE_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    /**
     * @return true if remote name is actually used in the entries
     */
    boolean isRemoteNameUsed() {
      String text = myRemoteNameTextField.getText();
      @NonNls String tagsPrefix = tagRemoteName(text, "");
      for (RefMapping m : myMapping) {
        if (m.local.startsWith(tagsPrefix)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Clear the mapping
     */
    public void clear() {
      myMapping.clear();
      fireTableDataChanged();
    }

    /**
     * @return a list of references
     */
    public String[] getReferences() {
      final int n = myMapping.size();
      String[] rc = new String[n];
      for (int i = 0; i < n; i++) {
        rc[i] = myMapping.get(i).toString();
      }
      return rc;
    }

    /**
     * Reference mapping object used in the table model
     */
    class RefMapping {
      /**
       * if true update is forced
       */
      boolean force;
      /**
       * remote reference name
       */
      String remote;
      /**
       * local reference name
       */
      String local;

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
        return (force ? "+" : "") + remote + ":" + local;
      }
    }
  }

  /**
   * The source of default references
   */
  public enum ReferenceSource {
    /**
     * The references are pulled from fetch specification
     */
    FETCH,
    /**
     * The references are pulled from push specification
     */
    PUSH, }
}
