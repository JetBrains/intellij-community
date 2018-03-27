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
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.mergeTool.MergeVersion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class MultipleFileMergeDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(MultipleFileMergeDialog.class);

  private JPanel myRootPanel;
  private JButton myAcceptYoursButton;
  private JButton myAcceptTheirsButton;
  private JButton myMergeButton;
  private TableView<VirtualFile> myTable;
  private JBLabel myDescriptionLabel;
  private final MergeProvider myProvider;
  @Nullable private final MergeSession myMergeSession;
  private final List<VirtualFile> myFiles;
  private final ListTableModel<VirtualFile> myModel;
  @Nullable
  private final Project myProject;
  private final ProjectManagerEx myProjectManager;
  private final List<VirtualFile> myProcessedFiles = new SmartList<>();
  private final Set<VirtualFile> myBinaryFiles = new HashSet<>();
  private final MergeDialogCustomizer myMergeDialogCustomizer;

  private final VirtualFileRenderer myVirtualFileRenderer = new VirtualFileRenderer();

  public MultipleFileMergeDialog(@Nullable Project project, @NotNull final List<VirtualFile> files, @NotNull final MergeProvider provider,
                                 @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    super(project);

    myProject = project;
    myProjectManager = ProjectManagerEx.getInstanceEx();
    myProjectManager.blockReloadingProjectOnExternalChanges();
    myFiles = new ArrayList<>(files);
    myProvider = provider;
    myMergeDialogCustomizer = mergeDialogCustomizer;

    final String description = myMergeDialogCustomizer.getMultipleFileMergeDescription(files);
    if (!StringUtil.isEmptyOrSpaces(description)) {
      myDescriptionLabel.setText(description);
    }

    List<ColumnInfo> columns = new ArrayList<>();
    columns.add(new ColumnInfo<VirtualFile, VirtualFile>(VcsBundle.message("multiple.file.merge.column.name")) {
      @Override
      public VirtualFile valueOf(final VirtualFile virtualFile) {
        return virtualFile;
      }

      @Override
      public TableCellRenderer getRenderer(final VirtualFile virtualFile) {
        return myVirtualFileRenderer;
      }

      @Nullable
      @Override
      public Comparator<VirtualFile> getComparator() {
        return VirtualFileComparator.INSTANCE;
      }
    });
    columns.add(new ColumnInfo<VirtualFile, String>(VcsBundle.message("multiple.file.merge.column.type")) {
      @Override
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
    });
    if (myProvider instanceof MergeProvider2) {
      myMergeSession = ((MergeProvider2)myProvider).createMergeSession(files);
      Collections.addAll(columns, myMergeSession.getMergeInfoColumns());
    }
    else {
      myMergeSession = null;
    }
    myModel = new ListTableModel<>(columns.toArray(new ColumnInfo[columns.size()]));
    myModel.setItems(new ArrayList<>(myFiles));
    myTable.setModelAndUpdateColumns(myModel);
    myVirtualFileRenderer.setFont(UIUtil.getListFont());
    myTable.setRowHeight(myVirtualFileRenderer.getPreferredSize().height);
    setTitle(myMergeDialogCustomizer.getMultipleFileDialogTitle());
    init();
    myAcceptYoursButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        acceptRevision(MergeSession.Resolution.AcceptedYours);
      }
    });
    myAcceptTheirsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        acceptRevision(MergeSession.Resolution.AcceptedTheirs);
      }
    });
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull final ListSelectionEvent e) {
        updateButtonState();
      }
    });
    for (VirtualFile file : files) {
      if (file.getFileType().isBinary() || provider.isBinary(file)) {
        myBinaryFiles.add(file);
      }
    }
    myTable.getSelectionModel().setSelectionInterval(0, 0);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        showMergeDialog();
        return true;
      }
    }.installOn(myTable);
    new TableSpeedSearch(myTable, o -> {
      if (o instanceof VirtualFile) {
        return ((VirtualFile)o).getName();
      }
      return null;
    });
  }

  private void updateButtonState() {
    boolean haveSelection = myTable.getSelectedRowCount() > 0;
    boolean haveUnmergeableFiles = false;
    for (VirtualFile file : myTable.getSelection()) {
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

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @NotNull
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

  protected boolean beforeResolve(Collection<VirtualFile> files) {
    return true;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "MultipleFileMergeDialog";
  }

  private void acceptRevision(@NotNull MergeSession.Resolution resolution) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final Collection<VirtualFile> files = myTable.getSelection();
    if (!beforeResolve(files)) {
      return;
    }

    try {
      for (VirtualFile file : files) {
        acceptFileRevision(file, resolution);
        checkMarkModifiedProject(file);
        markFileProcessed(file, resolution);
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      Messages.showErrorDialog(myRootPanel, "Error saving merged data: " + e.getMessage());
    }

    updateModelFromFiles();
  }

  private void acceptFileRevision(@NotNull VirtualFile file, @NotNull MergeSession.Resolution resolution) throws Exception {
    if (myMergeSession != null && !myMergeSession.canMerge(file)) return;

    if (myMergeSession != null && myMergeSession.acceptFileRevision(file, resolution)) return;

    if (!DiffUtil.makeWritable(myProject, file)) {
      throw new IOException("File is read-only: " + file.getPresentableName());
    }

    boolean isCurrent = resolution == MergeSession.Resolution.AcceptedYours;
    MergeData data = myProvider.loadRevisions(file);

    Ref<Exception> ex = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          if (isCurrent) {
            file.setBinaryContent(data.CURRENT);
          }
          else {
            file.setBinaryContent(data.LAST);
          }
        }
        catch (Exception e) {
          ex.set(e);
        }
      });
    }, "Accept " + (isCurrent ? "Yours" : "Theirs"), null);

    if (!ex.isNull()) throw ex.get();
  }

  private void markFileProcessed(@NotNull VirtualFile file, @NotNull MergeSession.Resolution resolution) {
    myFiles.remove(file);
    if (myMergeSession != null) {
      myMergeSession.conflictResolvedForFile(file, resolution);
    }
    else {
      myProvider.conflictResolvedForFile(file);
    }
    myProcessedFiles.add(file);
    if (myProject != null) {
      VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
    }
  }

  private void updateModelFromFiles() {
    if (myFiles.isEmpty()) {
      doCancelAction();
    }
    else {
      int selIndex = myTable.getSelectionModel().getMinSelectionIndex();
      myModel.setItems(new ArrayList<>(myFiles));
      if (selIndex >= myFiles.size()) {
        selIndex = myFiles.size() - 1;
      }
      myTable.getSelectionModel().setSelectionInterval(selIndex, selIndex);
    }
  }

  private void showMergeDialog() {
    DiffRequestFactory requestFactory = DiffRequestFactory.getInstance();
    Collection<VirtualFile> files = myTable.getSelection();
    if (!beforeResolve(files)) {
      return;
    }
    
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

      String leftTitle = myMergeDialogCustomizer.getLeftPanelTitle(file);
      String baseTitle = myMergeDialogCustomizer.getCenterPanelTitle(file);
      String rightTitle = myMergeDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER);
      String title = myMergeDialogCustomizer.getMergeWindowTitle(file);

      final List<byte[]> byteContents = ContainerUtil.list(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST);
      List<String> contentTitles = ContainerUtil.list(leftTitle, baseTitle, rightTitle);

      Consumer<MergeResult> callback = result -> {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null) FileDocumentManager.getInstance().saveDocument(document);
        checkMarkModifiedProject(file);

        if (result != MergeResult.CANCEL) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            markFileProcessed(file, getSessionResolution(result));
          });
        }
      };

      MergeRequest request;
      try {
        if (myProvider.isBinary(file)) { // respect MIME-types in svn
          request = requestFactory.createBinaryMergeRequest(myProject, file, byteContents, title, contentTitles, callback);
        }
        else {
          request = requestFactory.createMergeRequest(myProject, file, byteContents, title, contentTitles, callback);
        }

        MergeUtil.putRevisionInfos(request, mergeData);
      }
      catch (InvalidDiffRequestException e) {
        LOG.error(e);
        Messages.showErrorDialog(myRootPanel, "Can't show merge dialog");
        break;
      }

      DiffManager.getInstance().showMerge(myProject, request);
    }
    updateModelFromFiles();
  }

  @NotNull
  private static MergeSession.Resolution getSessionResolution(@NotNull MergeResult result) {
    switch (result) {
      case LEFT:
        return MergeSession.Resolution.AcceptedYours;
      case RIGHT:
        return MergeSession.Resolution.AcceptedTheirs;
      case RESOLVED:
        return MergeSession.Resolution.Merged;
      default:
        throw new IllegalArgumentException(result.name());
    }
  }

  private void checkMarkModifiedProject(@NotNull VirtualFile file) {
    MergeVersion.MergeDocumentVersion.reportProjectFileChangeIfNeeded(myProject, file);
  }

  private void createUIComponents() {
    Action mergeAction = new AbstractAction() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        showMergeDialog();
      }
    };
    mergeAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    myMergeButton = createJButtonForAction(mergeAction);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  @NotNull
  public List<VirtualFile> getProcessedFiles() {
    return myProcessedFiles;
  }

  private class VirtualFileRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      VirtualFile vf = (VirtualFile)value;
      setIcon(VirtualFilePresentation.getIcon(vf));
      append(vf.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      final VirtualFile parent = vf.getParent();
      if (parent != null) {
        append(" (" + FileUtil.toSystemDependentName(parent.getPresentableUrl()) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      SpeedSearchUtil.applySpeedSearchHighlighting(myTable, this, true, selected);
    }
  }

  private static class VirtualFileComparator implements Comparator<VirtualFile> {
    public static final VirtualFileComparator INSTANCE = new VirtualFileComparator();

    @Override
    public int compare(VirtualFile file1, VirtualFile file2) {
      int delta = StringUtil.naturalCompare(file1.getName(), file2.getName());
      if (delta != 0) return delta;

      VirtualFile parent1 = file1.getParent();
      VirtualFile parent2 = file2.getParent();
      String path1 = parent1 != null ? parent1.getPath() : null;
      String path2 = parent2 != null ? parent2.getPath() : null;
      return StringUtil.naturalCompare(path1, path2);
    }
  }
}
