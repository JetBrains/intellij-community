package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.changeBrowser.CvsChangeList;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagsPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  public static final ColumnInfo<VcsFileRevision, String> STATE = new ColumnInfo<VcsFileRevision, String>(
    CvsBundle.message("file.history.state.column.name")) {
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

  abstract class TagOrBranchColumn extends ColumnInfo {
    public TagOrBranchColumn(final String name) {
      super(name);
    }

    public TableCellRenderer getRenderer(Object object) {
      final TableCellRenderer rendererFromSuper = super.getRenderer(object);
      if (!(object instanceof CvsFileRevision)) return rendererFromSuper;
      final Collection tags = getValues((CvsFileRevision)object);
      if (tags.size() < 2) return rendererFromSuper;
      return new TagsPanel(getName());
    }

    public boolean isCellEditable(Object object) {
      if (!(object instanceof CvsFileRevision)) return false;
      return getValues(((CvsFileRevision)object)).size() > 1;
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
          TagsPanel result = new TagsPanel(getName());
          result.setTags(getValues((CvsFileRevision)object));
          result.setSelected(true, table);
          return result;
        }
      };
    }

    protected abstract Collection<String> getValues(CvsFileRevision revision);

    public Object valueOf(Object object) {
      if (!(object instanceof CvsFileRevision)) return "";
      Collection values = getValues(((CvsFileRevision)object));
      if (values.isEmpty()) return "";
      if (values.size() == 1) return values.iterator().next().toString();
      return values;
    }


  }

  private final ColumnInfo TAG = new TagOrBranchColumn(CvsBundle.message("file.history.tag.column.name")) {
    protected Collection<String> getValues(CvsFileRevision revision) {
      return revision.getTags();
    }
  };

  public final ColumnInfo BRANCHES = new TagOrBranchColumn(CvsBundle.message("file.history.branches.column.name")) {
    protected Collection<String> getValues(CvsFileRevision revision) {
      return revision.getBranches();
    }
  };


  private final Project myProject;

  public CvsHistoryProvider(Project project) {
    myProject = project;
  }

  public boolean isDateOmittable() {
    return false;
  }

  public ColumnInfo[] getRevisionColumns() {
    return new ColumnInfo[]{
      STATE, TAG, BRANCHES
    };
  }

  public String getHelpId() {
    return "cvs.selectionHistory";
  }

  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) {
    final List<VcsFileRevision> fileRevisionList = createRevisions(filePath);
    if (fileRevisionList == null) return null;
    return new VcsHistorySession(fileRevisionList) {
      @Nullable
      public VcsRevisionNumber calcCurrentRevisionNumber() {
        return getCurrentRevision(filePath);
      }

      @Override
      public synchronized boolean refresh() {
        //noinspection SimplifiableIfStatement
        if (!CvsEntriesManager.getInstance().isActive()) {
          return false;
        }
        return super.refresh();
      }

      public boolean isContentAvailable(final VcsFileRevision revision) {
        if (revision instanceof CvsFileRevision) {
          final CvsFileRevision cvsFileRevision = (CvsFileRevision)revision;
          return !cvsFileRevision.getState().equals(CvsChangeList.DEAD_STATE);
        }
        return super.isContentAvailable(revision);
      }
    };
  }

  private static VcsRevisionNumber getCurrentRevision(FilePath filePath) {
    Entry entryFor = CvsEntriesManager.getInstance().getEntryFor(filePath.getVirtualFileParent(),
                                                                 filePath.getName());
    if (entryFor == null) {
      return new CvsRevisionNumber("0");
    }
    else {
      return new CvsRevisionNumber(entryFor.getRevision());
    }

  }

  @Nullable
  public List<VcsFileRevision> createRevisions(final FilePath filePath) {
    final ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    final VirtualFile root = CvsVfsUtil.refreshAndFindFileByIoFile(filePath.getIOFile().getParentFile());
    // check if we have a history pane open for a file in a package which has just been deleted
    if (root == null) return null;
    final LocalPathIndifferentLogOperation logOperation =
      new LocalPathIndifferentLogOperation(filePath.getIOFile());
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.load.file.content"), logOperation),
                               new CvsOperationExecutorCallback() {
                                 public void executionFinished(boolean successfully) {
                                 }

                                 public void executeInProgressAfterAction(ModalityContext modaityContext) {
                                 }

                                 public void executionFinishedSuccessfully() {
                                   CvsConnectionSettings env = CvsEntriesManager.getInstance()
                                     .getCvsConnectionSettingsFor(filePath.getVirtualFileParent());
                                   final LogInformation firstLogInformation = logOperation.getFirstLogInformation();
                                   if (firstLogInformation != null) {
                                     result.addAll(createRevisionListOn(CvsUtil.getCvsLightweightFileForFile(filePath.getIOFile()),
                                                                        firstLogInformation, env, myProject));
                                   }
                                 }
                               });
    return result;

  }

  private static List<VcsFileRevision> createRevisionListOn(File file,
                                                            @NotNull LogInformation logInformation,
                                                            CvsEnvironment env,
                                                            Project project) {
    List revisionList = logInformation.getRevisionList();
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    for (final Object aRevisionList : revisionList) {
      Revision revision = (Revision)aRevisionList;
      result.add(new CvsFileRevisionImpl(revision, file, logInformation, env, project));
    }
    return result;
  }

  public AnAction[] getAdditionalActions(final FileHistoryPanel panel) {
    return AnAction.EMPTY_ARRAY;
  }

  public HistoryAsTreeProvider getTreeHistoryProvider() {
    return new MyHistoryAsTreeProvider();
  }

  private static class MyHistoryAsTreeProvider implements HistoryAsTreeProvider {
    public List<TreeItem<VcsFileRevision>> createTreeOn(List<VcsFileRevision> allRevisions) {
      List<VcsFileRevision> sortedRevisions = sortRevisions(allRevisions);

      List<TreeItem<VcsFileRevision>> result = new ArrayList<TreeItem<VcsFileRevision>>();

      TreeItem<VcsFileRevision> prevRevision = null;
      for (final VcsFileRevision sortedRevision : sortedRevisions) {
        CvsFileRevisionImpl cvsFileRevision = (CvsFileRevisionImpl)sortedRevision;
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

    @Nullable
    private static TreeItem<VcsFileRevision> getCommonParent(TreeItem<VcsFileRevision> prevRevision, TreeItem<VcsFileRevision> cvsFileRevision) {
      if (prevRevision == null) return null;
      while (!isParent(prevRevision, cvsFileRevision)) {
        prevRevision = prevRevision.getParent();
      }
      return prevRevision;
    }

    private static boolean isParent(TreeItem<VcsFileRevision> prevRevision, TreeItem<VcsFileRevision> cvsFileRevision) {
      if (prevRevision == null) return true;
      CvsFileRevisionImpl prevData = (CvsFileRevisionImpl)prevRevision.getData();
      CvsFileRevisionImpl data = (CvsFileRevisionImpl)cvsFileRevision.getData();
      return data.getRevisionNumber().asString().startsWith(prevData.getRevisionNumber().asString());
    }

    private static List<VcsFileRevision> sortRevisions(List<VcsFileRevision> revisionsList) {
      Collections.sort(revisionsList, new Comparator<VcsFileRevision>() {
        public int compare(VcsFileRevision rev1, VcsFileRevision rev2) {
          return VcsHistoryUtil.compare(rev1, rev2);
        }
      });
      return revisionsList;
    }
  }
}
