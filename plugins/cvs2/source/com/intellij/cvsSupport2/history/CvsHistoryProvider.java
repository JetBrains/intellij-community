package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotater;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagsPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataConstants;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class CvsHistoryProvider implements VcsHistoryProvider {
  public static final ColumnInfo<VcsFileRevision, String> STATE = new ColumnInfo<VcsFileRevision, String>("State") {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof CvsFileRevision)) return "";
      return ((CvsFileRevision)vcsFileRevision).getState();
    }

    public Comparator<VcsFileRevision> getComparator() {
      return new Comparator<VcsFileRevision>() {
        public int compare(VcsFileRevision r1, VcsFileRevision r2) {
          if (!(r1 instanceof CvsFileRevision)) return 1;
          if (!(r2 instanceof CvsFileRevision)) return -1;
          return ((CvsFileRevision)r1).getState().compareTo(((CvsFileRevision)r2).getState());
        }
      };
    }
  };

  private final ColumnInfo TAG = new ColumnInfo("Tag") {

    public Object valueOf(Object object) {
      if (!(object instanceof CvsFileRevision)) return "";
      Collection tags = ((CvsFileRevision)object).getTags();
      if (tags.isEmpty()) return "";
      if (tags.size() == 1) return tags.iterator().next().toString();
      return tags;
    }


    public TableCellRenderer getRenderer(Object object) {
      final TableCellRenderer rendererFromSuper = super.getRenderer(object);
      if (!(object instanceof CvsFileRevision)) return rendererFromSuper;
      final Collection tags = ((CvsFileRevision)object).getTags();
      if (tags.size() < 2) return rendererFromSuper;
      return new TagsPanel(myProject);
    }

    public boolean isCellEditable(Object object) {
      if (!(object instanceof CvsFileRevision)) return false;
      return ((CvsFileRevision)object).getTags().size() > 1;
    }

    public TableCellEditor getEditor(final Object object) {
      if (!(object instanceof CvsFileRevision)) return null;
      return new AbstractTableCellEditor() {
        public Object getCellEditorValue() {
          return "";
        }

        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column) {
          TagsPanel result = new TagsPanel(myProject);
          result.setTags(((CvsFileRevision)object).getTags());
          result.setSelected(true, table);
          return result;
        }
      };
    }
  };

  public static final ColumnInfo<VcsFileRevision, String> BRANCHES = new ColumnInfo<VcsFileRevision, String>("Branches") {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof CvsFileRevision)) return "";
      String result = ((CvsFileRevision)vcsFileRevision).getBranches();
      return result == null ? "" : result;
    }
  };


  private final Project myProject;

  public CvsHistoryProvider(Project project) {
    myProject = project;
  }

  public ColumnInfo[] getRevisionColumns() {
    return new ColumnInfo[]{
      STATE, TAG, BRANCHES
    };
  }

  public String getHelpId() {
    return "cvs.selectionHistory";
  }

  public VcsHistorySession createSessionFor(FilePath filePath) {
    return new VcsHistorySession(createRevisions(filePath), getCurrentRevision(filePath));
  }

  private VcsRevisionNumber getCurrentRevision(FilePath filePath) {
    Entry entryFor = CvsEntriesManager.getInstance().getEntryFor(filePath.getVirtualFileParent(),
                                                                 filePath.getName());
    if (entryFor == null) {
      return new CvsRevisionNumber("0");
    }
    else {
      return new CvsRevisionNumber(entryFor.getRevision());
    }

  }

  private List<VcsFileRevision> createRevisions(final FilePath filePath) {
    final ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    final LocalPathIndifferentLogOperation logOperation =
      new LocalPathIndifferentLogOperation(filePath.getIOFile());
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler("Load File Content", logOperation),
                               new CvsOperationExecutorCallback() {
                                 public void executionFinished(boolean successfully) {
                                 }

                                 public void executeInProgressAfterAction(ModalityContext modaityContext) {
                                 }

                                 public void executionFinishedSuccessfully() {
                                   CvsConnectionSettings env = CvsEntriesManager.getInstance()
                                     .getCvsConnectionSettingsFor(filePath.getVirtualFileParent());
                                   result.addAll(createRevisionListOn(CvsUtil.getCvsLightweightFileForFile(filePath.getIOFile()),
                                                                      logOperation.getFirstLogInformation(), env, myProject));
                                 }
                               });
    return result;

  }

  private List<VcsFileRevision> createRevisionListOn(File file, LogInformation logInformation,
                                                     CvsEnvironment env, Project project) {
    List revisionList = logInformation.getRevisionList();
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    for (Iterator each = revisionList.iterator(); each.hasNext();) {
      Revision revision = (Revision)each.next();
      result.add(new CvsFileRevisionImpl(revision, file, logInformation, env, project));
    }
    return result;
  }


  public AnAction[] getAdditionalActions() {
    return new AnAction[]{new AnAction("Annotate", "Annotate file", IconLoader.getIcon("/actions/annotate.png")) {
      public void update(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        FilePath filePath = (FilePath)dataContext.getData(VcsDataConstants.FILE_PATH);
        if (filePath == null) {
          e.getPresentation().setEnabled(false);
          return;
        }
        VirtualFile vFile = filePath.getVirtualFile();
        FileType fileType = vFile == null ? FileTypeManager.getInstance().getFileTypeByFileName(filePath.getName())
                            : vFile.getFileType();
        CvsFileRevision revision = (CvsFileRevision)dataContext.getData(VcsDataConstants.VCS_FILE_REVISION);
        VirtualFile revisionVirtualFile = (VirtualFile)dataContext.getData(VcsDataConstants.VCS_VIRTUAL_FILE);
        e.getPresentation().setEnabled(revision != null &&
                                       revisionVirtualFile != null
                                       && !fileType.isBinary());
      }

      public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        FilePath filePath = (FilePath)dataContext.getData(VcsDataConstants.FILE_PATH);
        CvsFileRevision revision = (CvsFileRevision)dataContext.getData(VcsDataConstants.VCS_FILE_REVISION);
        VirtualFile revisionVirtualFile = (VirtualFile)dataContext.getData(VcsDataConstants.VCS_VIRTUAL_FILE);
        new Annotater(CvsUtil.getCvsLightweightFileForFile(filePath.getIOFile()),
                      myProject,
                      revisionVirtualFile,
                      revision.getRevisionNumber().asString(),
                      CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(filePath.getVirtualFileParent())).execute();

      }
    }};
  }

  public HistoryAsTreeProvider getTreeHistoryProvider() {
    return new MyHistoryAsTreeProvider();
  }

  private static class MyHistoryAsTreeProvider implements HistoryAsTreeProvider {
    public List<TreeItem<VcsFileRevision>> createTreeOn(List<VcsFileRevision> allRevisions) {
      List<VcsFileRevision> sortedRevisions = sortRevisions(allRevisions);

      List<TreeItem<VcsFileRevision>> result = new ArrayList<TreeItem<VcsFileRevision>>();

      TreeItem<VcsFileRevision> prevRevision = null;
      for (Iterator each = sortedRevisions.iterator(); each.hasNext();) {
        CvsFileRevisionImpl cvsFileRevision = (CvsFileRevisionImpl)each.next();
        TreeItem<VcsFileRevision> treeItem = new TreeItem<VcsFileRevision>(cvsFileRevision);
        TreeItem<VcsFileRevision> commonParent = getCommonParent(prevRevision, treeItem);
        if (commonParent != null) {
          commonParent.addChild(treeItem);
        }
        else {
          result.add(treeItem);
        }

        prevRevision = treeItem;
      }

      return result;

    }

    private TreeItem<VcsFileRevision> getCommonParent(TreeItem<VcsFileRevision> prevRevision, TreeItem<VcsFileRevision> cvsFileRevision) {
      if (prevRevision == null) return null;
      while (!isParent(prevRevision, cvsFileRevision)) {
        prevRevision = prevRevision.getParent();
      }
      return prevRevision;
    }

    private boolean isParent(TreeItem<VcsFileRevision> prevRevision, TreeItem<VcsFileRevision> cvsFileRevision) {
      if (prevRevision == null) return true;
      CvsFileRevisionImpl prevData = (CvsFileRevisionImpl)prevRevision.getData();
      CvsFileRevisionImpl data = (CvsFileRevisionImpl)cvsFileRevision.getData();
      return data.getRevisionNumber().asString().startsWith(prevData.getRevisionNumber().asString());
    }

    private List<VcsFileRevision> sortRevisions(List<VcsFileRevision> revisionsList) {
      Collections.sort(revisionsList, new Comparator<VcsFileRevision>() {
        public int compare(VcsFileRevision rev1, VcsFileRevision rev2) {
          return VcsHistoryUtil.compare(rev1, rev2);
        }
      });
      return revisionsList;
    }
  }
}
