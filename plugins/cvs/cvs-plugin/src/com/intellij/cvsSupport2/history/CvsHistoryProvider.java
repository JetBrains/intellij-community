/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsFilePath;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.changeBrowser.CvsChangeList;
import com.intellij.cvsSupport2.changeBrowser.CvsRepositoryLocation;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.DefaultCvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagsPanel;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
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
      return (r1, r2) -> {
        if (!(r1 instanceof CvsFileRevision)) return 1;
        if (!(r2 instanceof CvsFileRevision)) return -1;
        return ((CvsFileRevision)r1).getState().compareTo(((CvsFileRevision)r2).getState());
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
          final TagsPanel result = new TagsPanel(getName());
          result.setTags(getValues((CvsFileRevision)object));
          result.setSelected(true, table);
          return result;
        }
      };
    }

    protected abstract Collection<String> getValues(CvsFileRevision revision);

    public Object valueOf(Object object) {
      if (!(object instanceof CvsFileRevision)) return "";
      final Collection values = getValues(((CvsFileRevision)object));
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

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[]{
      STATE, TAG, BRANCHES
    });
  }

  public String getHelpId() {
    return null;
  }

  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) {
    final List<VcsFileRevision> fileRevisionList = createRevisions(filePath);
    if (fileRevisionList == null) return null;
    return new MyHistorySession(fileRevisionList, filePath);
  }

  private static class MyHistorySession extends VcsAbstractHistorySession {
    private final FilePath myFilePath;

    private MyHistorySession(List<? extends VcsFileRevision> revisions, FilePath filePath) {
      super(revisions);
      myFilePath = filePath;
    }

    @Nullable
    public VcsRevisionNumber calcCurrentRevisionNumber() {
      return myFilePath == null ? null : getCurrentRevision(myFilePath);
    }

    @Override
    public synchronized boolean shouldBeRefreshed() {
      //noinspection SimplifiableIfStatement
      if (!CvsEntriesManager.getInstance().isActive()) {
        return false;
      }
      return super.shouldBeRefreshed();
    }

    public boolean isContentAvailable(final VcsFileRevision revision) {
      if (revision instanceof CvsFileRevision) {
        final CvsFileRevision cvsFileRevision = (CvsFileRevision)revision;
        return !cvsFileRevision.getState().equals(CvsChangeList.DEAD_STATE);
      }
      return super.isContentAvailable(revision);
    }

    public HistoryAsTreeProvider getHistoryAsTreeProvider() {
      return MyHistoryAsTreeProvider.getInstance();
    }

    @Override
    public VcsHistorySession copy() {
      return new MyHistorySession(getRevisionList(), myFilePath);
    }
  }

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsHistorySession session;
    if (path instanceof CvsFilePath) {
      final CvsRepositoryLocation location = ((CvsFilePath)path).getRepositoryLocation();
      final List<VcsFileRevision> fileRevisionList = createRevisions(location.getEnvironment(), path.getIOFile());
      if (fileRevisionList == null) return;
      session = new MyHistorySession(fileRevisionList, path);
    }
    else {
      session = createSessionFor(path);
    }
    partner.reportCreatedEmptySession((VcsAbstractHistorySession)session);
  }

  private static VcsRevisionNumber getCurrentRevision(FilePath filePath) {
    final Entry entryFor = CvsEntriesManager.getInstance().getEntryFor(filePath.getVirtualFileParent(), filePath.getName());
    if (entryFor == null) {
      return new CvsRevisionNumber("0");
    }
    else {
      return new CvsRevisionNumber(entryFor.getRevision());
    }
  }

  @Nullable
  public List<VcsFileRevision> createRevisions(final FilePath filePath) {
    final File file = filePath.getIOFile();
    final VirtualFile root = CvsVfsUtil.refreshAndFindFileByIoFile(file.getParentFile());
    // check if we have a history pane open for a file in a package which has just been deleted
    if (root == null) return null;
    final CvsConnectionSettings env = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(filePath.getVirtualFileParent());
    final File lightweightFileForFile = CvsUtil.getCvsLightweightFileForFile(file);
    return createRevisions(env, lightweightFileForFile);
  }

  private List<VcsFileRevision> createRevisions(final CvsEnvironment connectionSettings, final File lightweightFileForFile) {
    final LocalPathIndifferentLogOperation logOperation = new LocalPathIndifferentLogOperation(connectionSettings);
    logOperation.addFile(lightweightFileForFile);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    final ArrayList<VcsFileRevision> result = new ArrayList<>();
    executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.load.file.content"), logOperation),
                               new DefaultCvsOperationExecutorCallback() {
                                 @Override
                                 public void executionFinishedSuccessfully() {
                                   final LogInformation firstLogInformation = logOperation.getFirstLogInformation();
                                   if (firstLogInformation != null) {
                                     final List<Revision> revisionList = firstLogInformation.getRevisionList();
                                     for (Revision revision : revisionList) {
                                       result.add(new CvsFileRevisionImpl(revision, lightweightFileForFile,
                                                                          firstLogInformation, connectionSettings, myProject));
                                     }
                                   }
                                 }
                               });
    Collections.sort(result, Collections.reverseOrder(VcsFileRevisionComparator.INSTANCE));
    return result;
  }

  public AnAction[] getAdditionalActions(final Runnable refresher) {
    return new AnAction[]{ ShowAllAffectedGenericAction.getInstance() };
  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return null;
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    return true;
  }

  private static class MyHistoryAsTreeProvider implements HistoryAsTreeProvider {
    private static final MyHistoryAsTreeProvider ourInstance = new MyHistoryAsTreeProvider();

    public static MyHistoryAsTreeProvider getInstance() {
      return ourInstance;
    }

    public List<TreeItem<VcsFileRevision>> createTreeOn(List<VcsFileRevision> allRevisions) {
      Collections.sort(allRevisions, VcsFileRevisionComparator.INSTANCE);

      final List<TreeItem<VcsFileRevision>> result = new ArrayList<>();

      TreeItem<VcsFileRevision> prevRevision = null;
      for (final VcsFileRevision sortedRevision : allRevisions) {
        final CvsFileRevisionImpl cvsFileRevision = (CvsFileRevisionImpl)sortedRevision;
        final TreeItem<VcsFileRevision> treeItem = new TreeItem<>(cvsFileRevision);
        final TreeItem<VcsFileRevision> commonParent = getCommonParent(prevRevision, treeItem);
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
      final CvsFileRevisionImpl prevData = (CvsFileRevisionImpl)prevRevision.getData();
      final CvsFileRevisionImpl data = (CvsFileRevisionImpl)cvsFileRevision.getData();
      return data.getRevisionNumber().asString().startsWith(prevData.getRevisionNumber().asString());
    }
  }

  private static class VcsFileRevisionComparator implements Comparator<VcsFileRevision> {

    public static final VcsFileRevisionComparator INSTANCE = new VcsFileRevisionComparator();

    private VcsFileRevisionComparator() {}

    public int compare(VcsFileRevision rev1, VcsFileRevision rev2) {
      return VcsHistoryUtil.compare(rev1, rev2);
    }
  }
}
