package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataConstants;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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
          result.setTags(getValues((CvsFileRevision)object));
          result.setSelected(true, table);
          return result;
        }
      };
    }


    protected abstract Collection getValues(CvsFileRevision revision);

    public Object valueOf(Object object) {
      if (!(object instanceof CvsFileRevision)) return "";
      Collection values = getValues(((CvsFileRevision)object));
      if (values.isEmpty()) return "";
      if (values.size() == 1) return values.iterator().next().toString();
      return values;
    }


  }

  private final ColumnInfo TAG = new TagOrBranchColumn(CvsBundle.message("file.history.tag.column.name")) {
    protected Collection getValues(CvsFileRevision revision) {
      return revision.getTags();
    }
  };

  public final ColumnInfo BRANCHES = new TagOrBranchColumn(CvsBundle.message("file.history.branches.column.name")) {
    protected Collection getValues(CvsFileRevision revision) {
      return revision.getBranches();
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

  public VcsHistorySession createSessionFor(final FilePath filePath) {
    return new VcsHistorySession(createRevisions(filePath)) {
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

  private List<VcsFileRevision> createRevisions(final FilePath filePath) {
    final ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
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
    return new AnAction[]{new AnAction(CvsBundle.message("annotate.action.name"), CvsBundle.message("annotate.action.description"), IconLoader.getIcon("/actions/annotate.png")) {
      public void update(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        FilePath filePath = (FilePath)dataContext.getData(VcsDataConstants.FILE_PATH);
        if (filePath == null) {
          e.getPresentation().setEnabled(false);
          return;
        }
        VirtualFile vFile = filePath.getVirtualFile();
        FileType fileType = vFile == null
                            ? FileTypeManager.getInstance().getFileTypeByFileName(filePath.getName())
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
        try {
          final FileAnnotation annotation = CvsVcs2.getInstance(myProject)
            .createAnnotation(CvsUtil.getCvsLightweightFileForFile(filePath.getIOFile()),
                              revisionVirtualFile, revision.getRevisionNumber()
              .asString(),
                              CvsEntriesManager.getInstance()
                                .getCvsConnectionSettingsFor(filePath.getVirtualFileParent()));
          AbstractVcsHelper.getInstance(myProject).showAnnotation(annotation, revisionVirtualFile);
        }
        catch (VcsException e1) {
          AbstractVcsHelper.getInstance(myProject).showError(e1, CvsBundle.message("operation.name.annotate"));
        }

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
