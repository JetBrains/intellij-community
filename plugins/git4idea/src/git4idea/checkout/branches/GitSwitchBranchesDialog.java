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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.validators.GitBranchNameValidator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * The switch branches dialog
 */
public class GitSwitchBranchesDialog extends DialogWrapper {
  /**
   * The prefix for remote references
   */
  public static final String REMOTES_PREFIX = "remotes/";
  /**
   * The branch configuration name text field
   */
  private JTextField myNameTextField;
  /**
   * The changes panel
   */
  private JPanel myChangesPanel;
  /**
   * The root panel
   */
  private JPanel myRoot;
  /**
   * The branches table
   */
  private JBTable myBranchesTable;
  /**
   * Changes to transfer to new configurations label
   */
  private JLabel myChangesLabel;
  /**
   * The changes tree
   */
  private final ChangesTreeList<Change> myChangesTree;
  /**
   * The project to use
   */
  private final Project myProject;
  /**
   * The target branch configuration
   */
  private final GitBranchConfiguration myTarget;
  /**
   * The configuration settings object
   */
  private final GitBranchConfigurations myConfig;
  /**
   * If true, the dialog was invoked to modify the current configuration
   */
  private final boolean myModify;
  /**
   * The list of branches to use
   */
  private final List<BranchDescriptor> myBranches;
  /**
   * The existing configuration names (used to simplify validation)
   */
  private Set<String> myExistingConfigNames;
  /**
   * Base project directory
   */
  private File myBaseFile;

  /**
   * The constructor
   *
   * @param project      the project
   * @param target       the target configuration
   * @param allChanges   the all changes
   * @param roots        the collection of roots
   * @param remoteBranch the remote branch
   * @param config       the configuration
   * @param isModify     the modify flag
   * @throws VcsException if there is a problem with detecting the current state
   */
  protected GitSwitchBranchesDialog(Project project,
                                    final GitBranchConfiguration target,
                                    final List<Change> allChanges,
                                    List<VirtualFile> roots,
                                    String remoteBranch, final GitBranchConfigurations config, boolean isModify) throws VcsException {
    super(project, true);
    setTitle(isModify ? "Modify Branch Configuration" : "Checkout Branch Configuration");
    assert (remoteBranch == null) || (target == null) : "There should be no target for remote branch";
    myTarget = target;
    myConfig = config;
    myModify = isModify;
    myProject = project;
    VirtualFile baseDir = project.getBaseDir();
    myBaseFile = baseDir == null ? null : new File(baseDir.getPath());
    myExistingConfigNames = myConfig.getConfigurationNames();
    myChangesTree = new ChangesTreeList<Change>(myProject,
                                                Collections.<Change>emptyList(),
                                                !myModify,
                                                true,
                                                null,
                                                RemoteRevisionsCache.getInstance(project).getChangesNodeDecorator()) {
      protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator) {
        TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
        return builder.buildModel(changes, changeNodeDecorator);
      }

      protected List<Change> getSelectedObjects(final ChangesBrowserNode<Change> node) {
        return node.getAllChangesUnder();
      }

      @Nullable
      protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change)o;
        }
        return null;
      }
    };
    if (remoteBranch != null) {
      myBranches = prepareBranchesForRemote(remoteBranch, roots);
    }
    else {
      myBranches = prepareBranchDescriptors(target, roots);
    }
    Collections.sort(myBranches, new Comparator<BranchDescriptor>() {
      @Override
      public int compare(BranchDescriptor o1, BranchDescriptor o2) {
        return o1.getRoot().compareTo(o2.getRoot());
      }
    });
    if (target == null) {
      myNameTextField.setText(generateNewConfigurationName());
    }
    else {
      myNameTextField.setText(target.getName());
    }
    myChangesTree.setChangesToDisplay(allChanges);
    myChangesTree.setIncludedChanges(Collections.<Change>emptyList());
    myChangesPanel.add(myChangesTree, BorderLayout.CENTER);
    myChangesLabel.setLabelFor(myChangesTree);
    if (myModify) {
      myChangesLabel.setText("Changes in the current configuration");
    }
    RootTableModel tableModel = new RootTableModel();
    myBranchesTable.setModel(tableModel);
    myBranchesTable.setDefaultRenderer(Pair.class, new PairTableRenderer());
    final TableColumnModel columns = myBranchesTable.getColumnModel();
    final PairTableRenderer renderer = new PairTableRenderer();
    for (Enumeration<TableColumn> cs = columns.getColumns(); cs.hasMoreElements();) {
      cs.nextElement().setCellRenderer(renderer);
    }
    TableColumn revisionColumn = columns.getColumn(RootTableModel.REVISION_COLUMN);
    revisionColumn.setCellEditor(new ReferenceEditor());
    TableColumn branchColumn = columns.getColumn(RootTableModel.NEW_BRANCH_COLUMN);
    branchColumn.setCellEditor(new BranchNameEditor());
    myNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        verify();
      }
    });
    tableModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        verify();
      }
    });
    verify();
    init();
  }

  /**
   * The show configuration dialog
   *
   * @param project      the project to use
   * @param target       the target configuration
   * @param allChanges   the collection of changes
   * @param roots        the vcs roots
   * @param remoteBranch the remote branch
   * @param config       the configuration
   * @param isModify     the modify mode flag
   * @return the dialog result object
   * @throws VcsException if there is a problem with accessing git
   */
  @Nullable
  public static Result showDialog(Project project,
                                  @Nullable GitBranchConfiguration target,
                                  final List<Change> allChanges,
                                  List<VirtualFile> roots,
                                  @Nullable String remoteBranch,
                                  final GitBranchConfigurations config,
                                  boolean isModify) throws VcsException {
    GitSwitchBranchesDialog d = new GitSwitchBranchesDialog(project, target, allChanges, roots, remoteBranch, config, isModify);
    d.show();
    if (d.isOK()) {
      return d.createResult();
    }
    else {
      return null;
    }
  }

  /**
   * @return create dialog result object basing on the dialog state
   */
  private Result createResult() {
    Result rc = new Result();
    String name = myNameTextField.getText().trim();
    if (myTarget == null) {
      rc.target = myConfig.createConfiguration(name);
    }
    else {
      rc.target = myTarget;
      rc.target.setName(name);
    }
    rc.changes = new ArrayList<Change>(myChangesTree.getIncludedChanges());
    for (BranchDescriptor d : myBranches) {
      if (d.root != null) {
        if (!StringUtil.isEmpty(d.newBranchName)) {
          final String ref = d.referenceToCheckout.trim();
          rc.referencesToUse.put(d.root, Pair.create(ref, d.referencesToSelect.contains(ref)));
          rc.target.setReference(d.root.getPath(), d.newBranchName.trim());
          rc.checkoutNeeded.add(d.root);
        }
        else {
          String ref = d.referenceToCheckout.trim();
          if (!d.referencesToSelect.contains(ref)) {
            ref = myConfig.detectTag(d.root, ref);
          }
          rc.target.setReference(d.root.getPath(), ref);
          if (!d.referenceToCheckout.equals(d.currentReference)) {
            rc.checkoutNeeded.add(d.root);
          }
        }
      }
    }
    return rc;
  }


  /**
   * Verify dialog state
   */
  private void verify() {
    String text = myNameTextField.getText().trim();
    if (text.length() == 0) {
      setError("Empty configuration name is not allowed.");
      return;
    }
    else if (myTarget != null && text.equals(myTarget.getName())) {
    }
    else if (myExistingConfigNames.contains(text)) {
      setError("There is another configuration with the same name");
      return;
    }
    for (BranchDescriptor d : myBranches) {
      switch (d.status) {
        case BRANCH_NAME_EXISTS:
          setError("Duplicate branch name for root " + d.getRoot());
          return;
        case INVALID_BRANCH_NAME:
          setError("Invalid branch name for root " + d.getRoot());
          return;
        case BAD_REVISION:
          setError("Invalid revision for root " + d.getRoot());
          return;
        case MISSING_REVISION:
          setError("The revision must be specified for root " + d.getRoot());
          return;
        case CHECKOUT_NEEDED:
        case NO_ACTION:
        case REMOVED_ROOT:
          break;
        default:
          throw new RuntimeException("Unexpected status: " + d.status);
      }
    }
    setError(null);
  }

  private void setError(String s) {
    setErrorText(s);
    setOKActionEnabled(s == null);
  }

  /**
   * Generate new configuration name basing on descriptor
   *
   * @return the generated configuration name
   */
  private String generateNewConfigurationName() {
    String name = null;
    for (BranchDescriptor d : myBranches) {
      if (d.newBranchName != null) {
        name = d.newBranchName;
        break;
      }
      if (d.existingBranches.contains(d.currentReference)) {
        name = d.currentReference;
      }
    }
    if (name == null) {
      name = "untitled";
    }
    if (myExistingConfigNames.contains(name)) {
      for (int i = 2; i < Integer.MAX_VALUE; i++) {
        String t = name + i;
        if (!myExistingConfigNames.contains(t)) {
          name = t;
          break;
        }
      }
    }
    return name;
  }

  /**
   * Prepare branches for the case of remote checkout
   *
   * @param remoteBranch the remote branch to checkout
   * @param roots        the collection of vcs roots
   * @return the list of descriptors for the remote
   * @throws VcsException if git failed
   */
  private List<BranchDescriptor> prepareBranchesForRemote(String remoteBranch, List<VirtualFile> roots)
    throws VcsException {
    assert roots.size() > 0;
    List<BranchDescriptor> rc = new ArrayList<BranchDescriptor>();
    HashSet<String> allBranches = new HashSet<String>();
    allBranches.addAll(myConfig.getConfigurationNames());
    final String qualifiedBranch = "remotes/" + remoteBranch;
    String firstRemote = remoteBranch.endsWith("/HEAD") ? null : qualifiedBranch;
    for (VirtualFile root : roots) {
      BranchDescriptor d = new BranchDescriptor();
      d.root = root;
      d.currentReference = myConfig.describeRoot(root);
      if (firstRemote == null) {
        firstRemote = resolveHead(qualifiedBranch, d.root.getPath());
      }
      d.referenceToCheckout = qualifiedBranch;
      GitBranch.listAsStrings(myProject, root, false, true, d.existingBranches, null);
      GitBranch.listAsStrings(myProject, root, true, true, d.referencesToSelect, null);
      allBranches.addAll(d.existingBranches);
      rc.add(d);
    }
    String candidate;
    if (firstRemote == null) {
      candidate = "untitled";
    }
    else {
      int p = firstRemote.indexOf('/', REMOTES_PREFIX.length() + 1);
      assert p > 0 && p < firstRemote.length() - 1 : "Unexpected format for remote branch: " + firstRemote;
      candidate = firstRemote.substring(p + 1);
    }
    String actual = null;
    if (!allBranches.contains(candidate)) {
      actual = candidate;
    }
    else {
      for (int i = 2; i < Integer.MAX_VALUE; i++) {
        String t = candidate + i;
        if (!allBranches.contains(t)) {
          actual = t;
          break;
        }
      }
      assert actual != null : "Unexpected number of branches: " + remoteBranch;
    }
    for (BranchDescriptor d : rc) {
      d.newBranchName = actual;
      d.updateStatus();
    }
    return rc;
  }

  /**
   * Get candidate new branch name from remote branch
   *
   * @param value the value used to guess new reference
   * @param d     a description to use
   * @return the candidate branch name
   */
  @Nullable
  private String getCandidateLocal(String value, BranchDescriptor d) {
    if (StringUtil.isEmpty(value) || !value.startsWith(REMOTES_PREFIX)) {
      return null;
    }
    int p = value.indexOf('/', REMOTES_PREFIX.length() + 1);
    String candidate = null;
    if (p != -1) {
      String c = value.substring(p + 1);
      if (!d.existingBranches.contains(c)) {
        candidate = c;
      }
      else {
        for (int i = 2; i < Integer.MAX_VALUE; i++) {
          String cn = c + i;
          if (!d.existingBranches.contains(cn)) {
            candidate = cn;
            break;
          }
        }
      }
      if ("HEAD".equals(candidate)) {
        final String rootPath = d.root.getPath();
        String newRef = resolveHead(value, rootPath);
        candidate = newRef == null ? null : getCandidateLocal(newRef, d);
      }
    }
    return candidate;
  }

  /**
   * Resolve remote had reference
   *
   * @param value    the reference to resolve
   * @param rootPath the root path
   * @return the resolved reference or null
   */
  @Nullable
  private static String resolveHead(String value, String rootPath) {
    if (!value.startsWith("remotes/")) {
      return null;
    }
    String newRef;
    try {
      final String refText =
        new String(FileUtil.loadFileText(new File(rootPath, ".git/refs/" + value), GitUtil.UTF8_ENCODING)).trim();
      String refsPrefix = "ref: refs/";
      if (refText.endsWith("/HEAD") || !refText.startsWith(refsPrefix)) {
        newRef = null;
      }
      else {
        newRef = refText.substring(refsPrefix.length());
      }
    }
    catch (Exception e) {
      newRef = null;
    }
    return newRef;
  }

  /**
   * Prepare branch descriptors for existing configuration
   *
   * @param target the target
   * @param roots  the vcs root
   * @return the list of branch descriptors
   * @throws VcsException in case of git error
   */
  private List<BranchDescriptor> prepareBranchDescriptors(
    GitBranchConfiguration target,
    List<VirtualFile> roots)
    throws VcsException {
    Map<String, String> map = target == null ? Collections.<String, String>emptyMap() : target.getReferences();
    List<BranchDescriptor> rc = new ArrayList<BranchDescriptor>();
    for (VirtualFile root : roots) {
      BranchDescriptor d = new BranchDescriptor();
      d.root = root;
      d.storedReference = map.remove(root.getPath());
      if (d.storedReference != null) {
        d.storedRoot = d.root.getPath();
      }
      d.currentReference = myConfig.describeRoot(root);
      if (d.storedReference != null && !myModify) {
        d.referenceToCheckout = d.storedReference;
      }
      else {
        d.referenceToCheckout = d.currentReference;
      }
      GitBranch.listAsStrings(myProject, root, false, true, d.existingBranches, null);
      GitBranch.listAsStrings(myProject, root, true, true, d.referencesToSelect, null);
      d.updateStatus();
      rc.add(d);
    }
    for (Map.Entry<String, String> m : map.entrySet()) {
      String root = m.getKey();
      String ref = m.getValue();
      BranchDescriptor d = new BranchDescriptor();
      d.storedReference = ref;
      d.storedRoot = root;
      d.referenceToCheckout = ref;
      d.updateStatus();
      rc.add(d);
    }
    return rc;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRoot;
  }

  /**
   * The table model that displays mapping for the vcs roots
   */
  class RootTableModel extends AbstractTableModel {
    /**
     * The vcs root
     */
    static final int ROOT_COLUMN = 0;
    /**
     * The revision
     */
    static final int REVISION_COLUMN = 1;
    /**
     * The name of branch to checkout
     */
    static final int NEW_BRANCH_COLUMN = 2;
    /**
     * The status
     */
    static final int STATUS_COLUMN = 3;
    /**
     * The total number of columns
     */
    static final int COLUMNS = STATUS_COLUMN + 1;

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
      return COLUMNS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      BranchDescriptor d = myBranches.get(rowIndex);
      if (d.root == null) {
        return false;
      }
      return columnIndex == REVISION_COLUMN || columnIndex == NEW_BRANCH_COLUMN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      String t = (String)aValue;
      BranchDescriptor d = myBranches.get(rowIndex);
      if (d.root == null) {
        return;
      }
      if (columnIndex == REVISION_COLUMN) {
        String currentCandidate = getCandidateLocal(d.referenceToCheckout, d);
        boolean isCurrentMatchCandidate = currentCandidate != null && currentCandidate.equals(d.newBranchName);
        d.referenceToCheckout = t;
        if ((StringUtil.isEmpty(d.newBranchName) || isCurrentMatchCandidate) &&
            t.startsWith(REMOTES_PREFIX) &&
            d.referencesToSelect.contains(t)) {
          String candidate = getCandidateLocal(t, d);
          if (candidate != null) {
            d.newBranchName = candidate;
          }
        }
      }
      else if (columnIndex == NEW_BRANCH_COLUMN) {
        d.newBranchName = t;
      }
      d.updateStatus();
      fireTableRowsUpdated(rowIndex, rowIndex);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      BranchDescriptor d = myBranches.get(rowIndex);
      switch (columnIndex) {
        case ROOT_COLUMN:
          return Pair.create(d.getRoot(), d.root != null);
        case REVISION_COLUMN:
          return Pair.create(d.referenceToCheckout, d.isReferenceValid);
        case NEW_BRANCH_COLUMN:
          return Pair.create(d.newBranchName == null ? "" : d.newBranchName, d.isNewBranchValid);
        case STATUS_COLUMN:
          switch (d.status) {
            case INVALID_BRANCH_NAME:
              return Pair.create("Invalid new branch name", false);
            case BAD_REVISION:
              return Pair.create("Invalid revision", false);
            case MISSING_REVISION:
              return Pair.create("Missing revision", false);
            case CHECKOUT_NEEDED:
              return Pair.create("Checkout", true);
            case REMOVED_ROOT:
              return Pair.create("Removed root", true);
            case NO_ACTION:
              return Pair.create("", true);
            case BRANCH_NAME_EXISTS:
              return Pair.create("Branch name exists", false);
            default:
              throw new IllegalStateException("Unknown status:" + d.status);
          }
        default:
          throw new IllegalStateException("Unknown column: " + columnIndex);
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
        case REVISION_COLUMN:
          return "Checkout";
        case NEW_BRANCH_COLUMN:
          return "As New Branch";
        case STATUS_COLUMN:
          return "Status";
        default:
          throw new IllegalStateException("Unknown column: " + column);
      }
    }
  }

  /**
   * The object representing row entry
   */
  class BranchDescriptor {
    /**
     * The root to checkout, if null means that the old root is missing.
     */
    VirtualFile root;
    /**
     * Stored root path
     */
    String storedRoot;
    /**
     * Stored reference
     */
    String storedReference;
    /**
     * The commit expression to checkout
     */
    String referenceToCheckout;
    /**
     * if true, branch name is valid
     */
    boolean isReferenceValid;
    /**
     * True if commit expression is valid
     */
    RootStatus status;
    /**
     * The name of of branch
     */
    String newBranchName;
    /**
     * if true, branch name is valid
     */
    boolean isNewBranchValid;
    /**
     * The current type
     */
    String currentReference;
    /**
     * The existing branches
     */
    HashSet<String> existingBranches = new HashSet<String>();
    /**
     * The existing branches
     */
    TreeSet<String> referencesToSelect = new TreeSet<String>();

    /**
     * Update status of the entry
     */
    void updateStatus() {
      if (root == null) {
        status = RootStatus.REMOVED_ROOT;
        return;
      }
      status = branchNameStatus(newBranchName);
      isNewBranchValid = status == null;
      isReferenceValid = true;
      if (referenceToCheckout == null) {
        status = RootStatus.MISSING_REVISION;
        isReferenceValid = false;
        return;
      }
      try {
        GitRevisionNumber.resolve(myProject, root, referenceToCheckout);
        if (status == null) {
          status = StringUtil.isEmpty(newBranchName) && currentReference.equals(storedReference)
                   ? RootStatus.NO_ACTION
                   : RootStatus.CHECKOUT_NEEDED;
        }
      }
      catch (VcsException e) {
        isReferenceValid = false;
        status = RootStatus.BAD_REVISION;
      }
    }

    /**
     * Get branch name status
     *
     * @param name the name to check
     * @return null if branch name is ok, or status describing the problem
     */
    @Nullable
    private RootStatus branchNameStatus(final String name) {
      RootStatus b = null;
      if (!StringUtil.isEmpty(name)) {
        if (!GitBranchNameValidator.INSTANCE.checkInput(name)) {
          b = RootStatus.INVALID_BRANCH_NAME;
        }
        if (existingBranches.contains(name)) {
          b = RootStatus.BRANCH_NAME_EXISTS;
        }
      }
      return b;
    }

    public String getRoot() {
      String path = root == null ? storedRoot : root.getPath();
      String relative = myBaseFile == null ? path : FileUtil.getRelativePath(myBaseFile, new File(path));
      return relative == null ? path : relative;
    }
  }


  /**
   * The root status
   */
  enum RootStatus {
    /**
     * The checkout is needed for the vcs root. Non-error status.
     */
    CHECKOUT_NEEDED,
    /**
     * No action needed for vcs root. Non-error status.
     */
    NO_ACTION,
    /**
     * The branch information does not represents a vcs root anymore. Non-error status.
     */
    REMOVED_ROOT,
    /**
     * The bad revision expression
     */
    MISSING_REVISION,
    /**
     * The revision expression is invalid or could not be evaluated
     */
    BAD_REVISION,
    /**
     * The bad name for the branch
     */
    INVALID_BRANCH_NAME,
    /**
     * The bad name for the branch
     */
    BRANCH_NAME_EXISTS
  }


  /**
   * Pair text renderer
   */
  static class PairTableRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      @SuppressWarnings({"unchecked"}) Pair<String, Boolean> p = (Pair<String, Boolean>)value;
      String t = p.first == null ? "" : p.first;
      if (p.second) {
        append(t);
      }
      else {
        append(t, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }


  /**
   * The editor for references
   */
  class ReferenceEditor extends AbstractTableCellEditor {
    /**
     * The root panel
     */
    private final JPanel myPanel = new JPanel(new GridBagLayout());
    /**
     * Combobox for the panel
     */
    private final JComboBox myComboBox = new JComboBox();

    /**
     * The constructor
     */
    private ReferenceEditor() {
      myComboBox.setEditable(true);
      myComboBox.setRenderer(new BasicComboBoxRenderer());
      myComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
      myPanel.add(myComboBox,
                  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0),
                                         0,
                                         0));
    }

    /**
     * {@inheritDoc}
     */
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      BranchDescriptor d = myBranches.get(row);
      myComboBox.removeAllItems();
      for (String s : d.referencesToSelect) {
        myComboBox.addItem(s);
      }
      myComboBox.setSelectedItem(d.referenceToCheckout);
      return myPanel;
    }

    /**
     * {@inheritDoc}
     */
    public Object getCellEditorValue() {
      return myComboBox.getSelectedItem();
    }
  }

  /**
   * The editor for branch names
   */
  class BranchNameEditor extends AbstractTableCellEditor {
    /**
     * The root panel
     */
    private final JPanel myPanel = new JPanel(new GridBagLayout());
    /**
     * Combobox for the panel
     */
    private final JTextField myTextField = new JTextField();
    /**
     * The values that considered invalid
     */
    private Set<String> myInvalidValues;
    /**
     * Default foregorund color (likely black one)
     */
    private Color myDefaultForeground;

    /**
     * The constructor
     */
    private BranchNameEditor() {
      myDefaultForeground = myTextField.getForeground();
      myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          String s = myTextField.getText();
          if ((myInvalidValues == null || !myInvalidValues.contains(s)) &&
              (s.length() == 0 || GitBranchNameValidator.INSTANCE.checkInput(s))) {
            myTextField.setForeground(myDefaultForeground);
          }
          else {
            myTextField.setForeground(Color.RED);
          }
        }
      });
      myPanel.add(myTextField,
                  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0),
                                         0,
                                         0));
    }

    /**
     * {@inheritDoc}
     */
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      BranchDescriptor d = myBranches.get(row);
      myInvalidValues = d.existingBranches;
      myTextField.setText(d.newBranchName == null ? "" : d.newBranchName);
      return myPanel;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    public Object getCellEditorValue() {
      String s = myTextField.getText().trim();
      return s.length() == 0 ? null : s;
    }
  }

  /**
   * The result of the dialog
   */
  public static class Result {
    /**
     * The roots for which checkout is needed
     */
    Collection<VirtualFile> checkoutNeeded = new ArrayList<VirtualFile>();
    /**
     * The set of selected changes to transfer to new configuration
     */
    List<Change> changes;
    /**
     * References to use for new branches
     */
    HashMap<VirtualFile, Pair<String, Boolean>> referencesToUse = new HashMap<VirtualFile, Pair<String, Boolean>>();
    /**
     * The target configuration
     */
    GitBranchConfiguration target;
  }
}
