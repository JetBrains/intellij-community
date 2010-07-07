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

package com.intellij.openapi.vcs.merge;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author yole
 */
public class MultipleFileMergeDialog extends DialogWrapper {
  private JPanel myRootPanel;
  private JButton myAcceptYoursButton;
  private JButton myAcceptTheirsButton;
  private JButton myMergeButton;
  private TableView<VirtualFile> myTable;
  private final MergeProvider myProvider;
  private final MergeSession myMergeSession;
  private final List<VirtualFile> myFiles;
  private final ListTableModel<VirtualFile> myModel;
  private final Project myProject;
  private final ProjectManagerEx myProjectManager;
  private final List<VirtualFile> myProcessedFiles = new ArrayList<VirtualFile>();
  private final Set<VirtualFile> myBinaryFiles = new HashSet<VirtualFile>();

  private final VirtualFileRenderer myVirtualFileRenderer = new VirtualFileRenderer();

  private final ColumnInfo<VirtualFile, VirtualFile> NAME_COLUMN =
    new ColumnInfo<VirtualFile, VirtualFile>(VcsBundle.message("multiple.file.merge.column.name")) {
      public VirtualFile valueOf(final VirtualFile virtualFile) {
        return virtualFile;
      }

      @Override
      public TableCellRenderer getRenderer(final VirtualFile virtualFile) {
        return myVirtualFileRenderer;
      }
    };

  private final ColumnInfo<VirtualFile, String> TYPE_COLUMN =
    new ColumnInfo<VirtualFile, String>(VcsBundle.message("multiple.file.merge.column.type")) {
      public String valueOf(final VirtualFile virtualFile) {
        return myBinaryFiles.contains(virtualFile)
               ? VcsBundle.message("multiple.file.merge.type.binary")
               : VcsBundle.message("multiple.file.merge.type.text");
      }

      @Override
      public String getMaxStringValue() {
        return VcsBundle.message("multiple.file.merge.type.binary");
      }

      @Override
      public int getAdditionalWidth() {
        return 10;
      }
    };

  public MultipleFileMergeDialog(Project project, final List<VirtualFile> files, final MergeProvider provider) {
    super(project, false);
    myProject = project;
    myProjectManager = ProjectManagerEx.getInstanceEx();
    myProjectManager.blockReloadingProjectOnExternalChanges();
    myFiles = new ArrayList<VirtualFile>(files);
    myProvider = provider;

    List<ColumnInfo> columns = new ArrayList<ColumnInfo>();
    Collections.addAll(columns, NAME_COLUMN, TYPE_COLUMN);
    if (myProvider instanceof MergeProvider2) {
      myMergeSession = ((MergeProvider2)myProvider).createMergeSession(files);
      Collections.addAll(columns, myMergeSession.getMergeInfoColumns());
    }
    else {
      myMergeSession = null;
    }
    myModel = new ListTableModel<VirtualFile>(columns.toArray(new ColumnInfo[columns.size()]));
    myModel.setItems(files);
    myTable.setModel(myModel);
    myVirtualFileRenderer.setFont(UIUtil.getListFont());
    myTable.setRowHeight(myVirtualFileRenderer.getPreferredSize().height);
    setTitle(VcsBundle.message("multiple.file.merge.title"));
    init();
    myAcceptYoursButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        acceptRevision(true);
      }
    });
    myAcceptTheirsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        acceptRevision(false);
      }
    });
    myMergeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showMergeDialog();
      }
    });
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtonState();
      }
    });
    for (VirtualFile file : files) {
      if (file.getFileType().isBinary() || provider.isBinary(file)) {
        myBinaryFiles.add(file);
      }
    }
    myTable.getSelectionModel().setSelectionInterval(0, 0);
  }

  private void updateButtonState() {
    boolean haveSelection = myTable.getSelectedRowCount() > 0;
    boolean haveUnmergeableFiles = false;
    for (VirtualFile file : myTable.getSelection()) {
      if (myBinaryFiles.contains(file)) {
        haveUnmergeableFiles = true;
        break;
      }
      if (myMergeSession != null) {
        boolean canMerge = myMergeSession.canMerge(file);
        if (!canMerge) {
          haveUnmergeableFiles = true;
          break;
        }
      }
    }
    myAcceptYoursButton.setEnabled(haveSelection);
    myAcceptTheirsButton.setEnabled(haveSelection);
    myMergeButton.setEnabled(haveSelection && !haveUnmergeableFiles);
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected Action getCancelAction() {
    Action action = super.getCancelAction();
    action.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    return action;
  }

  @Override
  protected void dispose() {
    myProjectManager.unblockReloadingProjectOnExternalChanges();
    super.dispose();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "MultipleFileMergeDialog";
  }

  private void acceptRevision(final boolean isCurrent) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final Collection<VirtualFile> files = myTable.getSelection();
    for (final VirtualFile file : files) {
      final Ref<Exception> ex = new Ref<Exception>();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            if (!(myProvider instanceof MergeProvider2) || myMergeSession.canMerge(file)) {
              MergeData data = myProvider.loadRevisions(file);
              if (isCurrent) {
                file.setBinaryContent(data.CURRENT);
              }
              else {
                file.setBinaryContent(data.LAST);
                checkMarkModifiedProject(file);
              }
            }
            markFileProcessed(file, isCurrent ? MergeSession.Resolution.AcceptedYours : MergeSession.Resolution.AcceptedTheirs);
          }
          catch (Exception e) {
            ex.set(e);
          }
        }
      });
      if (!ex.isNull()) {
        Messages.showErrorDialog(myRootPanel, "Error saving merged data: " + ex.get().getMessage());
        break;
      }
    }
    updateModelFromFiles();
  }

  private void markFileProcessed(final VirtualFile file, final MergeSession.Resolution resolution) {
    myFiles.remove(file);
    if (myProvider instanceof MergeProvider2) {
      myMergeSession.conflictResolvedForFile(file, resolution);
    }
    else {
      myProvider.conflictResolvedForFile(file);
    }
    myProcessedFiles.add(file);
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
  }

  private void updateModelFromFiles() {
    if (myFiles.size() == 0) {
      doCancelAction();
    }
    else {
      int selIndex = myTable.getSelectionModel().getMinSelectionIndex();
      myModel.setItems(myFiles);
      if (selIndex >= myFiles.size()) {
        selIndex = myFiles.size() - 1;
      }
      myTable.getSelectionModel().setSelectionInterval(selIndex, selIndex);
    }
  }

  private void showMergeDialog() {
    final Collection<VirtualFile> files = myTable.getSelection();
    for (final VirtualFile file : files) {
      final MergeData mergeData;
      try {
        mergeData = myProvider.loadRevisions(file);
      }
      catch (VcsException ex) {
        Messages.showErrorDialog(myRootPanel, "Error loading revisions to merge: " + ex.getMessage());
        break;
      }

      if (mergeData.CURRENT == null || mergeData.LAST == null || mergeData.ORIGINAL == null) {
        Messages.showErrorDialog(myRootPanel, "Error loading revisions to merge");
        break;
      }

      final Document document = FileDocumentManager.getInstance().getDocument(file);
      final String contentWithMergeMarkers = document == null ? null : document.getText();

      String leftText = decodeContent(file, mergeData.CURRENT);
      String rightText = decodeContent(file, mergeData.LAST);
      String originalText = decodeContent(file, mergeData.ORIGINAL);

      DiffRequestFactory diffRequestFactory = DiffRequestFactory.getInstance();
      MergeRequest request = diffRequestFactory
        .createMergeRequest(leftText, rightText, originalText, file, myProject, ActionButtonPresentation.createApplyButton());
      String lastVersionTitle;
      if (mergeData.LAST_REVISION_NUMBER != null) {
        lastVersionTitle = VcsBundle.message("merge.version.title.last.version.number", mergeData.LAST_REVISION_NUMBER.asString());
      }
      else {
        lastVersionTitle = VcsBundle.message("merge.version.title.last.version");
      }
      request.setVersionTitles(
        new String[]{VcsBundle.message("merge.version.title.local.changes"), VcsBundle.message("merge.version.title.merge.result"),
          lastVersionTitle});
      request
        .setWindowTitle(VcsBundle.message("multiple.file.merge.request.title", FileUtil.toSystemDependentName(file.getPresentableUrl())));
      DiffManager.getInstance().getDiffTool().show(request);
      if (request.getResult() == DialogWrapper.OK_EXIT_CODE) {
        markFileProcessed(file, MergeSession.Resolution.Merged);
        checkMarkModifiedProject(file);
      }
      else {
        request.restoreOriginalContent();
      }
    }
    updateModelFromFiles();
  }

  private void checkMarkModifiedProject(final VirtualFile file) {
    if (file.getFileType() == StdFileTypes.IDEA_MODULE ||
        file.getFileType() == StdFileTypes.IDEA_PROJECT ||
        file.getFileType() == StdFileTypes.IDEA_WORKSPACE ||
        isProjectFile(file)) {
      myProjectManager.saveChangedProjectFile(file, myProject);
    }
  }

  private static boolean isProjectFile(VirtualFile file) {
    final ProjectOpenProcessor importProvider = ProjectOpenProcessor.getImportProvider(file);
    return importProvider != null && importProvider.lookForProjectsInDirectory();
  }

  private static String decodeContent(final VirtualFile file, final byte[] content) {
    return StringUtil.convertLineSeparators(file.getCharset().decode(ByteBuffer.wrap(content)).toString());
  }

  public List<VirtualFile> getProcessedFiles() {
    return myProcessedFiles;
  }

  private static class VirtualFileRenderer extends ColoredTableCellRenderer {
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      VirtualFile vf = (VirtualFile)value;
      setIcon(vf.getIcon());
      append(FileUtil.toSystemDependentName(vf.getPresentableUrl()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
