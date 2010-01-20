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

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PathMerger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooserPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class ApplyPatchDialog extends DialogWrapper {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchDialog");

  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JLabel myStatusLabel;
  private TextFieldWithBrowseButton myBaseDirectoryField;
  private JSpinner myStripLeadingDirectoriesSpinner;
  private JList myPatchContentsList;
  private ChangeListChooserPanel myChangeListChooser;
  private JButton myShowDiffButton;
  private List<FilePatch> myPatches;
  private Collection<FilePatch> myPatchesFailedToLoad;
  private final Alarm myLoadPatchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Alarm myVerifyPatchAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private String myLoadPatchError = null;
  private String myDetectedBaseDirectory = null;
  private int myDetectedStripLeadingDirs = -1;
  private final Project myProject;
  private boolean myInnerChange;
  private LocalChangeList mySelectedChangeList;

  private final Map<Pair<String, String>, String> myMoveRenameInfo;

  public ApplyPatchDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("patch.apply.dialog.title"));
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.PATCH || file.getFileType() == FileTypes.PLAIN_TEXT;
      }
    };
    myMoveRenameInfo = new HashMap<Pair<String, String>, String>();
    myFileNameField.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myFileNameField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateOKAction();
        myStatusLabel.setForeground(UIUtil.getLabelForeground());
        myStatusLabel.setText(VcsBundle.message("patch.load.progress"));
        myPatches = null;
        myMoveRenameInfo.clear();
        myLoadPatchAlarm.cancelAllRequests();
        myLoadPatchAlarm.addRequest(new Runnable() {
          public void run() {
            checkLoadPatches(true);
          }
        }, 400);
      }
    });

    myBaseDirectoryField.setText(project.getBaseDir().getPresentableUrl());
    myBaseDirectoryField.addBrowseFolderListener(VcsBundle.message("patch.apply.select.base.directory.title"), "", project,
                                                 new FileChooserDescriptor(false, true, false, false, false, false));
    myBaseDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myInnerChange) {
          queueVerifyPatchPaths();
        }
      }
    });

    myStripLeadingDirectoriesSpinner.setModel(new SpinnerNumberModel(0, 0, 256, 1));
    myStripLeadingDirectoriesSpinner.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        if (!myInnerChange) {
          queueVerifyPatchPaths();
        }
      }
    });

    myPatchContentsList.setCellRenderer(new PatchCellRendererPanel());

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
    myChangeListChooser.setDefaultSelection(changeListManager.getDefaultChangeList());
    myChangeListChooser.init(project);
    init();
    updateOKAction();
    myShowDiffButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showDiff();
      }
    });
    myPatchContentsList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getButton() == 1 && e.getClickCount() == 2) {
          showDiff();
        }
      }
    });

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    }.registerCustomShortcutSet(CommonShortcuts.getDiff(), myRootPanel, myDisposable);
  }

  private void showDiff() {
    List<Change> changes = new ArrayList<Change>();
    ApplyPatchContext context = getApplyPatchContext().getPrepareContext();
    Object[] selection = myPatchContentsList.getSelectedValues();
    if (selection.length == 0) {
      if (myPatches == null) return;
      selection = ArrayUtil.toObjectArray(myPatches);
    }
    for(Object o: selection) {
      final TextFilePatch patch = (TextFilePatch) o;
      try {
        if (patch.isNewFile()) {
          final FilePath newFilePath = FilePathImpl.createNonLocal(patch.getAfterName(), false);
          final String content = patch.getNewFileText();
          ContentRevision revision = new SimpleContentRevision(content, newFilePath, patch.getAfterVersionId());
          changes.add(new Change(null, revision));
        } else if ((! patch.isDeletedFile()) && (patch.getBeforeName() != null) && (patch.getAfterName() != null) &&
            (! patch.getBeforeName().equals(patch.getAfterName()))) {

          final VirtualFile baseDirectory = getBaseDirectory();
          final VirtualFile beforeFile = PathMerger.getFile(baseDirectory, patch.getBeforeName());

          if (beforeFile != null) {
            final List<String> tail = new ArrayList<String>();
            final VirtualFile partFile = PathMerger.getFile(baseDirectory, patch.getAfterName(), tail);
            final StringBuilder sb = new StringBuilder(partFile.getPath());
            for (String s : tail) {
              if (sb.charAt(sb.length() - 1) != '/') {
                sb.append('/');
              }
              sb.append(s);
            }

            final Change change =
                changeForPath(beforeFile, patch, FilePathImpl.createNonLocal(FileUtil.toSystemIndependentName(sb.toString()), false));
            if (change != null) {
              changes.add(change);
            }
          } else {
            Messages.showErrorDialog(myProject, "Cannot show difference: cannot find file " + patch.getBeforeName(),
                                     VcsBundle.message("patch.apply.dialog.title"));
          }
        }
          else {
          final VirtualFile fileToPatch = patch.findFileToPatch(context);
          if (fileToPatch != null) {
            final FilePathImpl filePath = new FilePathImpl(fileToPatch);
            final CurrentContentRevision currentRevision = new CurrentContentRevision(filePath);
            if (patch.isDeletedFile()) {
              changes.add(new Change(currentRevision, null));
            }
            else {
              final Change change = changeForPath(fileToPatch, patch, null);
              if (change != null) {
                changes.add(change);
              }
            }
          }
        }
      }
      catch (Exception e) {
        Messages.showErrorDialog(myProject, "Error loading changes for " + patch.getAfterFileName() + ": " + e.getMessage(),
                                 VcsBundle.message("patch.apply.dialog.title"));
        return;
      }
    }
    ShowDiffAction.showDiffForChange(changes.toArray(new Change[changes.size()]), 0, myProject,
                                     ShowDiffAction.DiffExtendUIFactory.NONE, false);
  }

  @Nullable
  private Change changeForPath(final VirtualFile fileToPatch, final TextFilePatch patch, final FilePath newFilePath) {
    try {
    final FilePathImpl filePath = new FilePathImpl(fileToPatch);
    final CurrentContentRevision currentRevision = new CurrentContentRevision(filePath);
    final Document doc = FileDocumentManager.getInstance().getDocument(fileToPatch);
    String baseContent = doc.getText();
    StringBuilder newText = new StringBuilder();
    patch.applyModifications(baseContent, newText);
    ContentRevision revision = new SimpleContentRevision(newText.toString(), (newFilePath == null) ? filePath : newFilePath, patch.getAfterVersionId());
    return new Change(currentRevision, revision);
    } catch (ApplyPatchException e) {
      ApplyPatchContext context = new ApplyPatchContext(getBaseDirectory(), 0, false, false);
      // just show diff here. maybe refactor further..
      ApplyPatchAction.mergeAgainstBaseVersion(myProject, fileToPatch, context, patch, ApplyPatchAction.ApplyPatchMergeRequestFactory.INSTANCE_READ_ONLY);
      return null;
    }
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.ApplyPatchDialog";
  }

  private void queueVerifyPatchPaths() {
    myStatusLabel.setForeground(UIUtil.getLabelForeground());
    myStatusLabel.setText(VcsBundle.message("apply.patch.progress.verifying"));
    myVerifyPatchAlarm.cancelAllRequests();
    myVerifyPatchAlarm.addRequest(new Runnable() {
      public void run() {
        try {
          if (myPatches != null) {
            verifyPatchPaths();
          }
        }
        catch(Exception ex) {
          LOG.error(ex);
        }
      }
    }, 400);
  }

  public void setFileName(String fileName) {
    myFileNameField.setText(fileName);
    checkLoadPatches(false);
  }

  private void checkLoadPatches(final boolean async) {
    final String fileName = myFileNameField.getText().replace(File.separatorChar, '/');
    final VirtualFile patchFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileName);
        if (file != null) {
          file.refresh(false, false);
          if (file.isDirectory()) {
            // we are looking for file not directory
            return null;
          }
        }
        return file;
      }
    });
    if (patchFile == null) {
      queueUpdateStatus("Cannot find patch file");
      return;
    }
    myChangeListChooser.setDefaultName(patchFile.getNameWithoutExtension().replace('_', ' ').trim());
    if (async) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          loadPatchesFromFile(patchFile);
        }
      });
    }
    else {
      loadPatchesFromFile(patchFile);
    }
  }

  private void loadPatchesFromFile(final VirtualFile patchFile) {
    myPatches = new ArrayList<FilePatch>();
    myPatchesFailedToLoad = new HashSet<FilePatch>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (!patchFile.isValid()) {
          queueUpdateStatus("Cannot find patch file");
          return;
        }
        PatchReader reader;
        try {
          reader = new PatchReader(patchFile);
        }
        catch (IOException e) {
          queueUpdateStatus(VcsBundle.message("patch.apply.open.error", e.getMessage()));
          return;
        }
        while(true) {
          FilePatch patch;
          try {
            patch = reader.readNextPatch();
          }
          catch (PatchSyntaxException e) {
            if (e.getLine() >= 0) {
              queueUpdateStatus(VcsBundle.message("patch.apply.load.error.line", e.getMessage(), e.getLine()));
            }
            else {
              queueUpdateStatus(VcsBundle.message("patch.apply.load.error", e.getMessage()));
            }
            return;
          }
          if (patch == null) {
            break;
          }

          final String beforeName = patch.getBeforeName();
          final String afterName = patch.getAfterName();
          final String movedMessage = RelativePathCalculator.getMovedString(beforeName, afterName);
          if (movedMessage != null) {
            myMoveRenameInfo.put(new Pair<String, String>(beforeName, afterName), movedMessage);
          }
          myPatches.add(patch);
        }
        if (myPatches.isEmpty()) {
          queueUpdateStatus(VcsBundle.message("patch.apply.no.patches.found"));
          return;
        }

        autoDetectBaseDirectory();
        queueUpdateStatus(null);
      }
    });
  }

  private void autoDetectBaseDirectory() {
    boolean autodetectFailed = false;
    for(FilePatch patch: myPatches) {
      VirtualFile baseDir = myDetectedBaseDirectory == null
                            ? getBaseDirectory()
                            : LocalFileSystem.getInstance().findFileByPath(myDetectedBaseDirectory.replace(File.separatorChar, '/'));
      int skipTopDirs = myDetectedStripLeadingDirs >= 0 ? myDetectedStripLeadingDirs : 0;
      VirtualFile fileToPatch;
      try {
        fileToPatch = patch.findFileToPatch(new ApplyPatchContext(baseDir, skipTopDirs, false, false));
      }
      catch (IOException e) {
        myPatchesFailedToLoad.add(patch);
        continue;
      }
      if (fileToPatch == null) {
        boolean success = false;
        if (!autodetectFailed) {
          String oldDetectedBaseDirectory = myDetectedBaseDirectory;
          int oldDetectedStripLeadingDirs = myDetectedStripLeadingDirs;
          success = detectDirectory(patch);
          if (success) {
            if ((oldDetectedBaseDirectory != null && !Comparing.equal(oldDetectedBaseDirectory, myDetectedBaseDirectory)) ||
                (oldDetectedStripLeadingDirs >= 0 && oldDetectedStripLeadingDirs != myDetectedStripLeadingDirs)) {
              myDetectedBaseDirectory = null;
              myDetectedStripLeadingDirs = -1;
              autodetectFailed = true;
            }
          }
        }
        if (!success) {
          myPatchesFailedToLoad.add(patch);
        }
      }
    }
  }

  private boolean detectDirectory(final FilePatch patch) {
    if (patch.getBeforeName().equals(patch.getAfterName()) && patch.isNewFile()) {
      return false;
    } else {
      boolean success = detectDirectoryByName(patch.getBeforeName());
      if (! success) {
        success = detectDirectoryByName(patch.getAfterName());
      }
      return success;
    }
  }

  private Collection<String> verifyPatchPaths() {
    final ApplyPatchContext context = getApplyPatchContext();
    myPatchesFailedToLoad.clear();
    for(FilePatch patch: myPatches) {
      try {
        if (context.getBaseDir() == null || patch.findFileToPatch(context) == null) {
          myPatchesFailedToLoad.add(patch);
        }
      }
      catch (IOException e) {
        myPatchesFailedToLoad.add(patch);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPatchContentsList.repaint();
        myStatusLabel.setText("");
      }
    });
    return context.getMissingDirectories();
  }

  private boolean detectDirectoryByName(final String patchFileName) {
    PatchBaseDirectoryDetector detector = PatchBaseDirectoryDetector.getInstance(myProject);
    if (detector == null) return false;
    final PatchBaseDirectoryDetector.Result result = detector.detectBaseDirectory(patchFileName);
    if (result == null) return false;
    myDetectedBaseDirectory = result.baseDir;
    myDetectedStripLeadingDirs = result.stripDirs;
    return true;
  }

  private void queueUpdateStatus(final String s) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          queueUpdateStatus(s);
        }
      });
      return;
    }
    updateStatus(s);
  }

  private void updateStatus(String s) {
    myInnerChange = true;
    try {
      if (myDetectedBaseDirectory != null) {
        myBaseDirectoryField.setText(myDetectedBaseDirectory);
        myDetectedBaseDirectory = null;
      }
      if (myDetectedStripLeadingDirs != -1) {
        myStripLeadingDirectoriesSpinner.setValue(myDetectedStripLeadingDirs);
        myDetectedStripLeadingDirs = -1;
      }
    }
    finally {
      myInnerChange = false;
    }
    myLoadPatchError = s;
    if (s == null) {
      myStatusLabel.setForeground(UIUtil.getLabelForeground());
      myStatusLabel.setText(buildPatchSummary());
    }
    else {
      myStatusLabel.setText(s);
      myStatusLabel.setForeground(Color.red);
    }
    updatePatchTableModel();
    updateOKAction();
  }

  private void updatePatchTableModel() {
    if (myPatches != null) {
      myPatchContentsList.setModel(new CollectionListModel(myPatches));
    }
    else {
      myPatchContentsList.setModel(new DefaultListModel());
    }
    myShowDiffButton.setEnabled(myPatches != null && myPatches.size() > 0);
  }

  private String buildPatchSummary() {
    int newFiles = 0;
    int changedFiles = 0;
    int deletedFiles = 0;
    for(FilePatch patch: myPatches) {
      if (patch.isNewFile()) {
        newFiles++;
      }
      else if (patch.isDeletedFile()) {
        deletedFiles++;
      }
      else {
        changedFiles++;
      }
    }
    StringBuilder summaryBuilder = new StringBuilder("<html><body><b>").append(VcsBundle.message("apply.patch.summary.title")).append("</b> ");
    appendSummary(changedFiles, 0, summaryBuilder, "patch.summary.changed.files");
    appendSummary(newFiles, changedFiles, summaryBuilder, "patch.summary.new.files");
    appendSummary(deletedFiles, changedFiles + newFiles, summaryBuilder, "patch.summary.deleted.files");
    summaryBuilder.append("</body></html>");
    return summaryBuilder.toString();
  }

  private static void appendSummary(final int count, final int prevCount, final StringBuilder summaryBuilder,
                                    @PropertyKey(resourceBundle = "messages.VcsBundle") final String key) {
    if (count > 0) {
      if (prevCount > 0) {
        summaryBuilder.append(", ");
      }
      summaryBuilder.append(VcsBundle.message(key, count));
    }
  }

  @Override
  protected void dispose() {
    myLoadPatchAlarm.dispose();
    myVerifyPatchAlarm.dispose();
    super.dispose();
  }

  private void updateOKAction() {
    setOKActionEnabled(myFileNameField.getText().length() > 0 && myLoadPatchError == null);
  }

  @Override
  protected void doOKAction() {
    if (myPatches == null) {
      myLoadPatchAlarm.cancelAllRequests();
      checkLoadPatches(false);
    }
    if (myLoadPatchError == null) {
      mySelectedChangeList = myChangeListChooser.getSelectedList(myProject);
      if (mySelectedChangeList == null) return;
      final Collection<String> missingDirs = verifyPatchPaths();
      if (missingDirs.size() > 0 && !checkCreateMissingDirs(missingDirs)) return;
      if (getBaseDirectory() == null) {
        Messages.showErrorDialog(getContentPane(), "Could not find patch base directory " + myBaseDirectoryField.getText());
        return;
      }
      super.doOKAction();
    }
  }

  private boolean checkCreateMissingDirs(final Collection<String> missingDirs) {
    StringBuilder messageBuilder = new StringBuilder(VcsBundle.message("apply.patch.create.dirs.prompt.header"));
    for(String missingDir: missingDirs) {
      messageBuilder.append(missingDir).append("\r\n");
    }
    messageBuilder.append(VcsBundle.message("apply.patch.create.dirs.prompt.footer"));
    int rc = Messages.showYesNoCancelDialog(myProject, messageBuilder.toString(), VcsBundle.message("patch.apply.dialog.title"),
                                            Messages.getQuestionIcon());
    if (rc == 0) {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          for(String dir: missingDirs) {
            try {
              VfsUtil.createDirectories(dir);
            }
            catch (IOException e) {
              Messages.showErrorDialog(myProject, "Error creating directories: " + e.getMessage(),
                                       VcsBundle.message("patch.apply.dialog.title"));
            }
          }
        }
      }, "Creating directories for new files in patch", null);
    }
    else if (rc != 1) {
      return false;
    }
    return true;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public List<FilePatch> getPatches() {
    return myPatches;
  }

  private VirtualFile getBaseDirectory() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(myBaseDirectoryField.getText())); 
    }
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(myBaseDirectoryField.getText()));
  }

  private int getStripLeadingDirectories() {
    return ((Integer) myStripLeadingDirectoriesSpinner.getValue()).intValue();
  }

  public ApplyPatchContext getApplyPatchContext() {
    return new ApplyPatchContext(getBaseDirectory(), getStripLeadingDirectories(), false, false);
  }

  public LocalChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  private static String getChangeType(final FilePatch filePatch) {
    if (filePatch.isNewFile()) return VcsBundle.message("change.type.new");
    if (filePatch.isDeletedFile()) return VcsBundle.message("change.type.deleted");
    return VcsBundle.message("change.type.modified");
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.vcs.patch.apply");
  }
  
  protected Action[] createActions() {
    return new Action[]{ getOKAction(), getCancelAction(), getHelpAction() };
  }

  private void createUIComponents() {
    myChangeListChooser = new ChangeListChooserPanel(null, new Consumer<String>() {
      public void consume(final String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage);
      }
    });
  }

  private class PatchCellRendererPanel extends JPanel implements ListCellRenderer {
    private final PatchCellRenderer myRenderer;
    private final JLabel myFileTypeLabel;

    public PatchCellRendererPanel() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      myRenderer = new PatchCellRenderer();
      add(myRenderer, BorderLayout.CENTER);
      myFileTypeLabel = new JLabel();
      myFileTypeLabel.setHorizontalAlignment(JLabel.RIGHT);
      add(myFileTypeLabel, BorderLayout.EAST);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      FilePatch patch = (FilePatch) value;
      myRenderer.getListCellRendererComponent(list, value, index, isSelected, false);
      myFileTypeLabel.setText("(" + getChangeType(patch) + ")");
      if (isSelected) {
        setBackground(UIUtil.getListSelectionBackground());
        setForeground(UIUtil.getListSelectionForeground());
        myFileTypeLabel.setForeground(UIUtil.getListSelectionForeground());
      }
      else {
        setBackground(UIUtil.getListBackground());
        setForeground(UIUtil.getListForeground());        
        myFileTypeLabel.setForeground(Color.gray);
      }
      return this;
    }
  }

  private class PatchCellRenderer extends ColoredListCellRenderer {
    private final SimpleTextAttributes myNewAttributes = new SimpleTextAttributes(0, FileStatus.ADDED.getColor());
    private final SimpleTextAttributes myDeletedAttributes = new SimpleTextAttributes(0, FileStatus.DELETED.getColor());
    private final SimpleTextAttributes myModifiedAttributes = new SimpleTextAttributes(0, FileStatus.MODIFIED.getColor());

    private boolean assumeProblemWillBeFixed(final FilePatch filePatch) {
      // if some of the files are valid, assume that "red" new files will be fixed by creating directories
      if (myPatches == null || myPatchesFailedToLoad == null) return false;
      return (filePatch.isNewFile() && myPatchesFailedToLoad.size() != myPatches.size());
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      FilePatch filePatch = (FilePatch) value;
      String name = filePatch.getAfterNameRelative(getStripLeadingDirectories());

      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
      setIcon(fileType.getIcon());

      if (myPatchesFailedToLoad.contains(filePatch) && !assumeProblemWillBeFixed(filePatch)) {
        append(name, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (filePatch.isNewFile()) {
        append(name, myNewAttributes);
      }
      else if (filePatch.isDeletedFile()) {
        append(name, myDeletedAttributes);
      }
      else {
        append(name, myModifiedAttributes);
      }

      final String afterPath = filePatch.getAfterName();
      final String beforePath = filePatch.getBeforeName();

      if ((beforePath != null) && (afterPath != null) && (! beforePath.equals(afterPath))) {
        final String message = myMoveRenameInfo.get(new Pair<String, String>(beforePath, afterPath));
        if (message != null) {
          append(message, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    }
  }
}
