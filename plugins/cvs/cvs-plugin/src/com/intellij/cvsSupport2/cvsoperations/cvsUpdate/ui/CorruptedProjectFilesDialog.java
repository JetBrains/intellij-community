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
package com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.MergedWithConflictProjectOrModuleFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Options;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.text.MessageFormat;

public class CorruptedProjectFilesDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui.CorruptedProjectFilesDialog");

  private JLabel myMessageLabel;
  private JLabel myIconLabel;
  private final java.util.List<MergedWithConflictProjectOrModuleFile> myCorruptedFiles;
  private final Project myProject;
  private JPanel myPanel;
  private JRadioButton myGetAllOption;
  private JRadioButton mySkipAllOption;
  private JRadioButton myShowDialogOption;

  public CorruptedProjectFilesDialog(Project project,
                                     Collection<MergedWithConflictProjectOrModuleFile> mergedFiles) {
    super(project, true);
    myCorruptedFiles = new ArrayList<>(mergedFiles);
    myProject = project;

    myIconLabel.setIcon(Messages.getInformationIcon());
    myIconLabel.setText("");

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myGetAllOption);
    buttonGroup.add(mySkipAllOption);
    buttonGroup.add(myShowDialogOption);

    myShowDialogOption.setSelected(true);

    setTitle(com.intellij.CvsBundle.message("operation.name.update"));
    init();
    showNextFileInfo();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{new SkipFile(), new GetFile(), new SkipAll(), new GetAll()
    //  , new VisualMerge()
    };
  }

  private void showNextFileInfo() {
    VirtualFile currentVirtualFile = getCurrentVirtualFile();
    FileType fileType = currentVirtualFile.getFileType();
    myMessageLabel.setText(
      com.intellij.CvsBundle.message("label.project.files.cannot.be.merged.without.conflict",

      fileType.getDescription(), currentVirtualFile.getPresentableUrl()));
    setTitle(com.intellij.CvsBundle.message("dialog.title.file.cannot.be.merged.without.conflicts", fileType.getDescription(), currentVirtualFile.getName()));
  }

  private void onCurrentFileProcessed(boolean isOk) {
    if (myCorruptedFiles.isEmpty()) {
      doCloseAction(isOk);
      return;
    }

    myCorruptedFiles.remove(0);

    if (myCorruptedFiles.isEmpty()) {
      doCloseAction(isOk);
    }
    else {
      showNextFileInfo();
    }
  }

  private void doCloseAction(boolean isOk) {
    if (isOk) doOKAction(); else doCancelAction();
  }

  private VirtualFile getCurrentVirtualFile() {
    return myCorruptedFiles.get(0).getOriginal();
  }

  private class SkipFile extends AbstractAction {
    public SkipFile() {
      putValue(NAME, com.intellij.CvsBundle.message("button.text.skip"));
    }

    public void actionPerformed(ActionEvent e) {
      onCurrentFileProcessed(false);
    }
  }

  private class GetFile extends AbstractAction {
    public GetFile() {
      putValue(NAME, com.intellij.CvsBundle.message("button.text.get"));
    }

    public void actionPerformed(ActionEvent e) {
      myCorruptedFiles.get(0).setShouldBeCheckedOut();
      onCurrentFileProcessed(true);
    }
  }

  private class SkipAll extends AbstractAction {
    public SkipAll() {
      putValue(NAME, com.intellij.CvsBundle.message("button.text.skip.all"));
    }

    public void actionPerformed(ActionEvent e) {
      myCorruptedFiles.clear();
      onCurrentFileProcessed(false);
    }
  }

  private class GetAll extends AbstractAction {
    public GetAll() {
      putValue(NAME, com.intellij.CvsBundle.message("button.text.get.all"));
    }

    public void actionPerformed(ActionEvent e) {
      for (final MergedWithConflictProjectOrModuleFile myCorruptedFile : myCorruptedFiles) {
        myCorruptedFile.setShouldBeCheckedOut();
      }
      myCorruptedFiles.clear();
      onCurrentFileProcessed(true);
    }
  }

  /*
  private class VisualMerge extends AbstractAction {
    public VisualMerge() {
      putValue(NAME, "Visual Merge");
    }

    public void actionPerformed(ActionEvent e) {
      try {
        VirtualFile currentVirtualFile = getCurrentVirtualFile();
        final Map<VirtualFile, List<String>> fileToRevisions = new com.intellij.util.containers.HashMap<VirtualFile, List<String>>();
        fileToRevisions.put(currentVirtualFile, CvsUtil.getAllRevisionsForFile(currentVirtualFile));
        CvsMergeAction internalMergeAction = new CvsMergeAction(currentVirtualFile, myProject, fileToRevisions, new AbstractMergeAction.FileValueHolder());
        Document conflictWasResolved = internalMergeAction.showMergeDialogForFile(null);
        if (conflictWasResolved != null) {
          saveExternally(currentVirtualFile, conflictWasResolved);
          onCurrentFileProcessed(false);
        }
      }
      catch (VcsException e1) {
        AbstractVcsHelper.getInstance(myProject).showErrors(new ArrayList<VcsException>(Collections.singleton(e1)), "Merge errors");
      }
    }

  }
  */

  private void saveExternally(VirtualFile currentVirtualFile, Document document) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    fileDocumentManager.saveDocument(fileDocumentManager.getDocument(currentVirtualFile));
    File ioProjectFile = VfsUtil.virtualToIoFile(currentVirtualFile);
    try {
      OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(ioProjectFile));
      try {
        FileUtil.copy(new ByteArrayInputStream(document.getText().getBytes(currentVirtualFile.getCharset().name())),
                      outputStream);
      }
      finally {
        outputStream.close();
      }

    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected void doOKAction() {
    saveShowDialogOptions();
    super.doOKAction();
  }

  public void doCancelAction() {
    saveShowDialogOptions();
    super.doCancelAction();
  }

  private void saveShowDialogOptions() {
    CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(myProject);
    if (myShowDialogOption.isSelected()) {
      cvsConfiguration.SHOW_CORRUPTED_PROJECT_FILES = Options.SHOW_DIALOG;
    }
    else if (mySkipAllOption.isSelected()) {
      cvsConfiguration.SHOW_CORRUPTED_PROJECT_FILES = Options.DO_NOTHING;
    }
    else if (myGetAllOption.isSelected()) {
      cvsConfiguration.SHOW_CORRUPTED_PROJECT_FILES = Options.PERFORM_ACTION_AUTOMATICALLY;
    }
  }
}