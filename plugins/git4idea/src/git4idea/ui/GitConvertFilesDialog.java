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

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * This dialog allows converting the specified files before committing them.
 */
public class GitConvertFilesDialog extends DialogWrapper {
  /**
   * The version when option --stdin was added
   */
  private static final GitVersion CHECK_ATTR_STDIN_SUPPORTED = new GitVersion(1, 6, 1, 0);
  /**
   * Do not convert exit code
   */
  public static final int DO_NOT_CONVERT = NEXT_USER_EXIT_CODE;
  /**
   * The checkbox used to indicate that dialog should not be shown
   */
  private JCheckBox myDoNotShowCheckBox;
  /**
   * The root panel of the dialog
   */
  private JPanel myRootPanel;
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
   * @param project     the project to which this dialog is related
   * @param filesToShow the files to show sorted by vcs root
   */
  GitConvertFilesDialog(Project project, Map<VirtualFile, Set<VirtualFile>> filesToShow) {
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
    TreeUtil.expandAll(myFilesToConvert);
    setTitle(GitBundle.getString("crlf.convert.title"));
    setOKButtonText(GitBundle.getString("crlf.convert.convert"));
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DoNotConvertAction(), getCancelAction()};
  }


  /**
   * Create custom UI components
   */
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
   * Check if files need to be converted to other line separator. The method could be invoked from non-UI thread.
   *
   * @param project       the project to use
   * @param settings      the vcs settings
   * @param sortedChanges sorted changes
   * @param exceptions    the collection with exceptions
   * @return true if conversion completed successfully, false if process was cancelled or there were errors
   */
  public static boolean showDialogIfNeeded(final Project project,
                                           final GitVcsSettings settings,
                                           Map<VirtualFile, List<Change>> sortedChanges,
                                           final List<VcsException> exceptions) {
    try {
      if (settings.askBeforeLineSeparatorConversion() ||
          settings.getLineSeparatorsConversion() == GitVcsSettings.ConversionPolicy.PROJECT_LINE_SEPARATORS) {
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
            String detectedLineSeparator = LoadTextUtil.detectLineSeparator(f, true);
            if (detectedLineSeparator == null || nl.equals(detectedLineSeparator)) {
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
            VirtualFile[] selectedFiles = null;
            if (settings.askBeforeLineSeparatorConversion()) {
              GitConvertFilesDialog d = new GitConvertFilesDialog(project, files);
              d.show();
              if (d.isOK()) {
                settings.setAskBeforeLineSeparatorConversion(!d.myDoNotShowCheckBox.isSelected());
                settings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.PROJECT_LINE_SEPARATORS);
                selectedFiles = d.myFilesToConvert.getCheckedNodes(VirtualFile.class, null);
              }
              else if (d.getExitCode() == DO_NOT_CONVERT) {
                settings.setAskBeforeLineSeparatorConversion(!d.myDoNotShowCheckBox.isSelected());
                settings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.NONE);
              }
              else {
                //noinspection ThrowableInstanceNeverThrown
                exceptions.add(new VcsException("Commit was cancelled in file conversion dialog"));
              }
            }
            else {
              ArrayList<VirtualFile> fileList = new ArrayList<VirtualFile>();
              for (Set<VirtualFile> fileSet : files.values()) {
                fileList.addAll(fileSet);
              }
              selectedFiles = VfsUtil.toVirtualFileArray(fileList);
            }
            if (selectedFiles != null) {
              for (VirtualFile f : selectedFiles) {
                try {
                  LoadTextUtil.changeLineSeparator(project, GitConvertFilesDialog.class.getName(), f, nl);
                }
                catch (IOException e) {
                  //noinspection ThrowableInstanceNeverThrown
                  exceptions.add(new VcsException("Failed to change line separators for the file: " + f.getPresentableUrl(), e));
                }
              }
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
    boolean stdin = CHECK_ATTR_STDIN_SUPPORTED.isLessOrEqual(GitVcs.getInstance(project).version());
    for (final Map.Entry<VirtualFile, Set<VirtualFile>> e : files.entrySet()) {
      final VirtualFile r = e.getKey();
      GitSimpleHandler h = new GitSimpleHandler(project, r, GitCommand.CHECK_ATTR);
      if (stdin) {
        h.addParameters("--stdin", "-z");
      }
      h.addParameters("crlf");
      h.setSilent(true);
      h.setNoSSH(true);
      final HashMap<String, VirtualFile> filesToCheck = new HashMap<String, VirtualFile>();
      Set<VirtualFile> fileSet = e.getValue();
      for (VirtualFile file : fileSet) {
        filesToCheck.put(GitUtil.relativePath(r, file), file);
      }
      if (stdin) {
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
      }
      else {
        h.endOptions();
        h.addRelativeFiles(filesToCheck.values());
      }
      StringScanner output = new StringScanner(h.run());
      String unsetIndicator = ": crlf: unset";
      while (output.hasMoreData()) {
        String l = output.line();
        if (l.endsWith(unsetIndicator)) {
          fileSet.remove(filesToCheck.get(GitUtil.unescapePath(l.substring(0, l.length() - unsetIndicator.length()))));
        }
      }
    }
  }

  /**
   * Action used to indicate that no conversion should be performed
   */
  class DoNotConvertAction extends AbstractAction {
    private static final long serialVersionUID = 1931383640152023206L;

    /**
     * The constructor
     */
    DoNotConvertAction() {
      putValue(NAME, GitBundle.getString("crlf.convert.leave"));
      putValue(DEFAULT_ACTION, Boolean.FALSE);
    }

    /**
     * {@inheritDoc}
     */
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
     *
     * @param r     a renderer to use
     * @param value the unknown value
     */
    private static void renderUnknown(ColoredTreeCellRenderer r, Object value) {
      r.append("UNSUPPORTED NODE TYPE: " + (value == null ? "null" : value.getClass().getName()), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
