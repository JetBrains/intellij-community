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
package git4idea.checkin;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * This dialog allows converting the specified files before committing them.
 */
public class GitConvertFilesDialog extends DialogWrapper {
  /**
   * The checkbox used to indicate that dialog should not be shown
   */
  private JCheckBox myDoNotShowCheckBox;
  /**
   * The root panel of the dialog
   */
  private JPanel myRootPanel;
  /**
   * The checkbox that disables conversion of files
   */
  private JCheckBox myDoNotConvertFilesCheckBox;
  /**
   * The tree of files to convert
   */
  private CheckboxTreeBase myFilesToConvert;
  /**
   * The root node in the tree
   */
  private CheckedTreeNode myRootNode;

  /**
   * The constructor
   *
   * @param project the project to which this dialog is related
   */
  GitConvertFilesDialog(Project project, GitVcsSettings settings, Map<VirtualFile, Set<VirtualFile>> filesToShow) {
    super(project, true);
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
    myDoNotConvertFilesCheckBox.setSelected(settings.LINE_SEPARATORS_CONVERSION == GitVcsSettings.ConversionPolicy.NONE);
    updateFields();
    TreeUtil.expandAll(myFilesToConvert);
    myDoNotConvertFilesCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateFields();
      }
    });
    setTitle(GitBundle.getString("crlf.convert.title"));
    init();
  }


  /**
   * Update fields basing on selection state
   */
  private void updateFields() {
    if (myDoNotConvertFilesCheckBox.isSelected()) {
      myRootNode.setChecked(false);
      myFilesToConvert.setEnabled(false);
      setOKButtonText(GitBundle.getString("crlf.convert.leave"));
    }
    else {
      myFilesToConvert.setEnabled(true);
      myRootNode.setChecked(true);
      setOKButtonText(GitBundle.getString("crlf.convert.convert"));
    }
  }


  /**
   * Create custom UI components
   */
  private void createUIComponents() {
    myRootNode = new CheckedTreeNode("ROOT");
    myFilesToConvert = new CheckboxTree(new FileTreeCellRenderer(), myRootNode);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * Check if files need to be converted to other line separator
   *
   * @param project       the project to use
   * @param settings      the vcs settings
   * @param sortedChanges sorted changes
   * @param exceptions    the collection with exceptions
   * @return true if conversion completed successfully, false if process was cancelled or there were errors
   */
  static boolean showDialogIfNeeded(final Project project,
                                    final GitVcsSettings settings,
                                    Map<VirtualFile, List<Change>> sortedChanges,
                                    final List<VcsException> exceptions) {
    try {
      if (settings.LINE_SEPARATORS_CONVERSION_ASK ||
          settings.LINE_SEPARATORS_CONVERSION == GitVcsSettings.ConversionPolicy.PROJECT_LINE_SEPARATORS) {
        LocalFileSystem lfs = LocalFileSystem.getInstance();
        final String nl = CodeStyleFacade.getInstance(project).getLineSeparator();
        final Map<VirtualFile, Set<VirtualFile>> files = new HashMap<VirtualFile, Set<VirtualFile>>();
        // preliminary screening of files
        for (Map.Entry<VirtualFile, List<Change>> entry : sortedChanges.entrySet()) {
          final VirtualFile root = entry.getKey();
          final Set<VirtualFile> added = new HashSet<VirtualFile>();
          for (Change change : entry.getValue()) {
            switch (change.getType()) {
              case NEW:
              case MODIFICATION:
              case MOVED:
                VirtualFile f = lfs.findFileByPath(change.getAfterRevision().getFile().getPath());
                if (f != null && !f.getFileType().isBinary() && !nl.equals(LoadTextUtil.detectLineSeparator(f, false))) {
                  added.add(f);
                }
                break;
              case DELETED:
            }
          }
          if (!added.isEmpty()) {
            files.put(root, added);
          }
        }
        // ignore files with CRLF unset
        ignoreFilesWithCrlfUnset(project, files);
        // check crlf for real
        for (Iterator<Map.Entry<VirtualFile, Set<VirtualFile>>> i = files.entrySet().iterator(); i.hasNext();) {
          Map.Entry<VirtualFile, Set<VirtualFile>> e = i.next();
          Set<VirtualFile> fs = e.getValue();
          for (Iterator<VirtualFile> j = fs.iterator(); j.hasNext();) {
            VirtualFile f = j.next();
            if (nl.equals(LoadTextUtil.detectLineSeparator(f, true))) {
              j.remove();
            }
          }
          if (fs.isEmpty()) {
            i.remove();
          }
        }
        if (files.isEmpty()) {
          return true;
        }
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            GitConvertFilesDialog d = new GitConvertFilesDialog(project, settings, files);
            d.show();
            if (d.isOK()) {
              settings.LINE_SEPARATORS_CONVERSION_ASK = d.myDoNotShowCheckBox.isSelected();
              if (d.myDoNotConvertFilesCheckBox.isSelected()) {
                settings.LINE_SEPARATORS_CONVERSION = GitVcsSettings.ConversionPolicy.NONE;
              }
              else {
                settings.LINE_SEPARATORS_CONVERSION = GitVcsSettings.ConversionPolicy.PROJECT_LINE_SEPARATORS;
                for (VirtualFile f : d.myFilesToConvert.getCheckedNodes(VirtualFile.class, null)) {
                  try {
                    LoadTextUtil.changeLineSeparator(project, d, f, nl);
                  }
                  catch (IOException e) {
                    //noinspection ThrowableInstanceNeverThrown
                    exceptions.add(new VcsException("Failed to change line separators for the file: " + f.getPresentableUrl(), e));
                  }
                }
              }
            }
            else {
              //noinspection ThrowableInstanceNeverThrown
              exceptions.add(new VcsException("Commit was cancelled in file conversion dialog"));
            }
          }
        });
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
    return exceptions.isEmpty();
  }

  /**
   * Remove files that have -crlf attribute specified
   *
   * @param project the context project
   * @param files   the files to check (map from vcs roots to the set of files under root)
   * @throws VcsException if there is problem with running git
   */
  private static void ignoreFilesWithCrlfUnset(Project project, Map<VirtualFile, Set<VirtualFile>> files) throws VcsException {
    for (final Map.Entry<VirtualFile, Set<VirtualFile>> e : files.entrySet()) {
      final VirtualFile r = e.getKey();
      GitSimpleHandler h = new GitSimpleHandler(project, r, GitHandler.CHECK_ATTR);
      h.addParameters("--stdin", "-z", "crlf");
      h.setSilent(true);
      h.setNoSSH(true);
      final HashMap<String, VirtualFile> filesToCheck = new HashMap<String, VirtualFile>();
      Set<VirtualFile> fileSet = e.getValue();
      for (VirtualFile file : fileSet) {
        filesToCheck.put(GitUtil.relativePath(r, file), file);
      }
      h.setInputProcessor(new Processor<OutputStream>() {
        public boolean process(OutputStream outputStream) {
          try {
            OutputStreamWriter out = new OutputStreamWriter(outputStream, GitUtil.UTF8_CHARSET);
            try {
              for (String file : filesToCheck.keySet()) {
                out.write(file);
                out.write("\u0000");
              }
            }
            finally {
              out.close();
            }
          }
          catch (IOException ex) {
            try {
              outputStream.close();
            }
            catch (IOException ioe) {
              // ignore exception
            }
          }
          return true;
        }
      });
      StringScanner output = new StringScanner(h.run());
      String unsetIndicator = ": crlf unset";
      while (output.hasMoreData()) {
        String l = output.line();
        if (l.endsWith(unsetIndicator)) {
          fileSet.remove(filesToCheck.get(GitUtil.unescapePath(l.substring(0, l.length() - unsetIndicator.length()))));
        }
      }
    }
  }


  /**
   * The cell renderer for the tree
   */
  static class FileTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    /**
     * {@inheritDoc}
     */
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
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
        r.append(GitUtil.getRelativeFilePath(file, parent), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      }
      else {
        // the vcs root node
        r.append(file.getPresentableUrl(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);
      }
    }

    /**
     * Render unknown node
     * @param r a renderer to use
     * @param value the unknown value
     */
    private static void renderUnknown(ColoredTreeCellRenderer r, Object value) {
      r.append("UNSUPPORTED NODE TYPE: "+(value == null?"null":value.getClass().getName()), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
