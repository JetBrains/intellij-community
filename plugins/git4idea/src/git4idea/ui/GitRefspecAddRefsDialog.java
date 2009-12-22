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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This dialog allows adding selected tag and branches are references.
 */
public class GitRefspecAddRefsDialog extends DialogWrapper {
  /**
   * Get references button
   */
  private JButton myGetRefsButton;
  /**
   * If selected, the branches are fetched by {@link #myGetRefsButton}
   */
  private JCheckBox myIncludeBranchesCheckBox;
  /**
   * If selected, the tags are fetched by {@link #myGetRefsButton}
   */
  private JCheckBox myIncludeTagsCheckBox;
  /**
   * The selector for tags and branches
   */
  private CheckboxTree myReferenceChooser;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * Root of the tree
   */
  private CheckedTreeNode myTreeRoot;
  /**
   * The git root of the repository
   */
  private final VirtualFile myRoot;
  /**
   * The name of the remote
   */
  private final String myRemote;
  /**
   * The set of tags
   */
  private final SortedSet<String> myTags;
  /**
   * The set of branches
   */
  private final SortedSet<String> myBranches;
  /**
   * The logger for the class
   */
  private static final Logger log = Logger.getInstance(GitRefspecAddRefsDialog.class.getName());

  /**
   * A constructor
   *
   * @param project  the project
   * @param root     the git repository root
   * @param remote   the remote name or url of remote repository
   * @param tags     the set of tags (might be modified if update button is pressed)
   * @param branches the set of branches (might be modified if update button is pressed)
   */
  protected GitRefspecAddRefsDialog(@NotNull Project project,
                                    @NotNull VirtualFile root,
                                    @NotNull String remote,
                                    @NotNull SortedSet<String> tags,
                                    @NotNull SortedSet<String> branches) {
    super(project, true);
    setTitle(GitBundle.getString("addrefspec.title"));
    setOKButtonText(GitBundle.getString("addrefspec.button"));
    myProject = project;
    myRoot = root;
    myRemote = remote;
    myTags = tags;
    myBranches = branches;
    updateTree();
    setupGetReferences();
    init();
    setOKActionEnabled(false);
  }


  /**
   * Set up action listener for {@link #myGetRefsButton}
   */
  private void setupGetReferences() {
    // setup enabled state
    final ActionListener enabledListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myGetRefsButton.setEnabled(myIncludeBranchesCheckBox.isSelected() || myIncludeTagsCheckBox.isSelected());
      }
    };
    myIncludeBranchesCheckBox.addActionListener(enabledListener);
    myIncludeTagsCheckBox.addActionListener(enabledListener);
    // perform update
    myGetRefsButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        GitSimpleHandler handler = new GitSimpleHandler(myProject, myRoot, GitCommand.LS_REMOTE);
        if (myIncludeBranchesCheckBox.isSelected()) {
          handler.addParameters("--heads");
          myBranches.clear();
        }
        if (myIncludeTagsCheckBox.isSelected()) {
          handler.addParameters("--tags");
          myTags.clear();
        }
        handler.addParameters(myRemote);
        String result = GitHandlerUtil
          .doSynchronously(handler, GitBundle.message("addrefspec.getting.references.title", myRemote), handler.printableCommandLine());
        if (result != null) {
          StringScanner s = new StringScanner(result);
          while (s.hasMoreData()) {
            s.tabToken(); // skip last commit hash
            String ref = s.line();
            if (ref.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
              myBranches.add(ref);
            }
            else if (ref.startsWith(GitTag.REFS_TAGS_PREFIX)) {
              myTags.add(ref);
            }
            else {
              log.warn("Unknwon reference type from ls-remote \"" + myRemote + "\" :" + ref);
            }
          }
        }
        updateTree();
      }
    });
  }

  /**
   * Update checkbox tree basing on the current state of the tag and branches set. The checkbox state is preserved. New items are created
   * in unselected state.
   */
  private void updateTree() {
    // save the previous selection
    HashSet<String> oldTags = new HashSet<String>();
    HashSet<String> oldBranches = new HashSet<String>();
    for (Reference ref : myReferenceChooser.getCheckedNodes(Reference.class, null)) {
      (ref.isTag ? oldTags : oldBranches).add(ref.name);
    }
    // clear the tree
    myTreeRoot.removeAllChildren();
    // fill tags and branches
    addReferences(false, oldBranches, myBranches, GitBundle.getString("addrefspec.node.branches"));
    addReferences(true, oldTags, myTags, GitBundle.getString("addrefspec.node.tags"));
    TreeUtil.expandAll(myReferenceChooser);
    myReferenceChooser.treeDidChange();
  }

  /**
   * Add references to the tree along with category node
   *
   * @param isTag   if true tag nodes are added
   * @param old     the set of old elements (used to select
   * @param current the current set of elements (after update)
   * @param name    the name of the set
   */
  private void addReferences(final boolean isTag, final HashSet<String> old, final SortedSet<String> current, @Nls final String name) {
    if (!current.isEmpty()) {
      final CheckedTreeNode tagsRoot = new CheckedTreeNode(name);
      for (String t : current) {
        final CheckedTreeNode node = new CheckedTreeNode(new Reference(isTag, t));
        node.setChecked(old.contains(t));
        tagsRoot.add(node);
      }
      myTreeRoot.add(tagsRoot);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return GitRefspecAddRefsDialog.class.getName();
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * Create UI components that require custom creation: {@link #myReferenceChooser}
   */
  private void createUIComponents() {
    myTreeRoot = new CheckedTreeNode("");
    myReferenceChooser = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {

      public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded,
                                        final boolean leaf,
                                        final int row,
                                        final boolean hasFocus) {
        final CheckedTreeNode node = (CheckedTreeNode)value;
        final Object userObject = node.getUserObject();
        String text;
        SimpleTextAttributes attributes;
        Icon icon;
        if (userObject == null) {
          // invisible root (do nothing)
          //noinspection HardCodedStringLiteral
          text = "INVISBLE ROOT";
          attributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
          icon = null;
        }
        else if (userObject instanceof String) {
          // category node (render as bold)
          text = (String)userObject;
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          icon = expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
        }
        else {
          // reference node
          text = ((Reference)userObject).name;
          attributes = node.isChecked() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
          icon = null;
        }
        final ColoredTreeCellRenderer textRenderer = getTextRenderer();
        if (icon != null) {
          textRenderer.setIcon(icon);
        }
        if (text != null) {
          textRenderer.append(text, attributes);
        }
      }
    }, myTreeRoot) {
      @Override
      protected void onNodeStateChanged(final CheckedTreeNode node) {
        boolean flag = node.isChecked() || myReferenceChooser.getCheckedNodes(Reference.class, null).length != 0;
        setOKActionEnabled(flag);
        super.onNodeStateChanged(node);
      }
    };
  }

  /**
   * Get selected elements
   *
   * @param isTag if true tags are returned, heads otherwise
   * @return a collection of selected reference of the specified type
   */
  public SortedSet<String> getSelected(final boolean isTag) {
    TreeSet<String> rc = new TreeSet<String>();
    final Reference[] checked = myReferenceChooser.getCheckedNodes(Reference.class, new Tree.NodeFilter<Reference>() {
      public boolean accept(final Reference node) {
        return node.isTag == isTag;
      }
    });
    for (Reference r : checked) {
      rc.add(r.name);
    }
    return rc;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Fetch.AddReference";
  }


  /**
   * A remote reference
   */
  static final class Reference {
    /**
     * If true, the name represents a tag. if false, the name represents the branch name.
     */
    final boolean isTag;
    /**
     * Name of the reference
     */
    final String name;

    /**
     * A constructor from fields
     *
     * @param tag  the value for {@link #isTag}
     * @param name the value for {@link #name}
     */
    public Reference(final boolean tag, final String name) {
      isTag = tag;
      this.name = name;
    }
  }
}
