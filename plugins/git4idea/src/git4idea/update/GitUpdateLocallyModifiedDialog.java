package git4idea.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The dialog that displays locally modified files during update process
 */
public class GitUpdateLocallyModifiedDialog extends DialogWrapper {
  /**
   * The rescan button
   */
  private JButton myRescanButton;
  /**
   * The list of files to revert
   */
  private JList myFilesList;
  /**
   * The git root label
   */
  private JLabel myGitRoot;
  /**
   * The root panel
   */
  private JPanel myRootPanel;
  /**
   * The vcs root
   */
  private VirtualFile myRoot;
  /**
   * The collection with locally modified files
   */
  private List<String> myLocallyModifiedFiles;

  /**
   * The constructor
   * @param project the current project
   * @param root the vcs root
   * @param locallyModifiedFiles the collection of locally modified files to use
   */
  protected GitUpdateLocallyModifiedDialog(final Project project, final VirtualFile root, List<String> locallyModifiedFiles) {
    super(project, true);
    myRoot = root;
    myLocallyModifiedFiles = locallyModifiedFiles;
    setTitle(GitBundle.getString("update.locally.modified.title"));
    myGitRoot.setText(myRoot.getPresentableUrl());
    myFilesList.setModel(new DefaultListModel());
    setOKButtonText(GitBundle.getString("update.locally.modified.revert"));
    syncListModel();
    myRescanButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myLocallyModifiedFiles.clear();
        try {
          scanFiles(project, root, myLocallyModifiedFiles);
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(project, ex, "Checking for locally modified files");
        }
      }
    });
    init();
  }

  /**
   * Refresh list model according to the current content of the collection
   */
  private void syncListModel() {
    DefaultListModel listModel = (DefaultListModel)myFilesList.getModel();
    listModel.removeAllElements();
    for(String p : myLocallyModifiedFiles) {
      listModel.addElement(p);
    }
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
   * Scan working tree and detect locally modified files
   *
   * @param project the project to scan
   * @param root the root to scan
   * @param files the collection with files
   * @throws VcsException if there problem with running git or working tree is dirty in unsupported way
   */
  private static void scanFiles(Project project, VirtualFile root, List<String> files) throws VcsException {
    String rootPath = root.getPath();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.DIFF);
    h.addParameters("--name-status");
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    StringScanner s = new StringScanner(h.run());
    while(s.hasMoreData()) {
      if(s.isEol()) {
        s.line();
        continue;
      }
      if(s.tryConsume("M\t")) {
        String path = rootPath + "/" + GitUtil.unescapePath(s.line());
        files.add(path);
      } else {
        throw new VcsException("Working tree is dirty in unsupported way: "+s.line());
      }
    }
  }


  /**
   * Show the dialog if needed
   *
   * @param project the project
   * @param root the vcs root
   * @return true if showing is not needed or operation completed successfully
   */
  public static boolean showIfNeeded(final Project project, final VirtualFile root) {
    final ArrayList<String> files = new ArrayList<String>();
    try {
      scanFiles(project, root, files);
      final AtomicBoolean rc = new AtomicBoolean(true);
      if(!files.isEmpty()) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            GitUpdateLocallyModifiedDialog d = new GitUpdateLocallyModifiedDialog(project, root, files);
            d.show();
            rc.set(d.isOK());
          }
        });
        if(rc.get()) {
          if(!files.isEmpty()) {
            revertFiles(project, root, files);
          }
        }
      }
      return rc.get();
    }
    catch (final VcsException e) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          GitUIUtil.showOperationError(project, e, "Checking for locally modified files");
        }
      });
      return false;
    }
  }

  /**
   * Revert files from the list
   *
   * @param project the project
   * @param root the vcs root
   * @param files the files to revert
   */
  private static void revertFiles(Project project, VirtualFile root, ArrayList<String> files) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.CHECKOUT);
    h.endOptions();
    h.setNoSSH(true);
    for(String p : files) {
      h.addRelativePaths(VcsUtil.getFilePath(p));
    }
    h.run();
  }
}
