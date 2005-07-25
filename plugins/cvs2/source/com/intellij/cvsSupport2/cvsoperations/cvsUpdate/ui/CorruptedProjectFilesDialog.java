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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

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
    myCorruptedFiles = new ArrayList<MergedWithConflictProjectOrModuleFile>(mergedFiles);
    myProject = project;

    myIconLabel.setIcon(Messages.getInformationIcon());
    myIconLabel.setText("");

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myGetAllOption);
    buttonGroup.add(mySkipAllOption);
    buttonGroup.add(myShowDialogOption);

    myShowDialogOption.setSelected(true);

    setTitle("Update");
    init();
    showNextFileInfo();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected Action[] createActions() {
    return new Action[]{new SkipFile(), new GetFile(), new SkipAll(), new GetAll()
    //  , new VisualMerge()
    };
  }

  private void showNextFileInfo() {
    VirtualFile currentVirtualFile = getCurrentVirtualFile();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(currentVirtualFile);
    myMessageLabel.setText("<html>" +
                           fileType.getDescription() +
                           "<br>" + currentVirtualFile.getPresentableUrl() +
                           "<br> cannot be merged without conflicts." +
                           "<br>Click 'Skip' to skip changes from repository." +
                           "<br>Click 'Get' to skip local changes and get repository version." +
                           "<br>Click 'Skip All' to skip changes from repository for this file and all remaining files." +
                           "<br>Click 'Get All' to skip local changes and get repository version for this file and all remaining files." +
                           "</html>");
    setTitle(fileType.getDescription() + " " + currentVirtualFile.getName() + " Cannot Be Merged Without Conflicts");
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
      putValue(NAME, "Skip");
    }

    public void actionPerformed(ActionEvent e) {
      onCurrentFileProcessed(false);
    }
  }

  private class GetFile extends AbstractAction {
    public GetFile() {
      putValue(NAME, "Get");
    }

    public void actionPerformed(ActionEvent e) {
      myCorruptedFiles.get(0).setShouldBeCheckedOut();
      onCurrentFileProcessed(true);
    }
  }

  private class SkipAll extends AbstractAction {
    public SkipAll() {
      putValue(NAME, "Skip All");
    }

    public void actionPerformed(ActionEvent e) {
      myCorruptedFiles.clear();
      onCurrentFileProcessed(false);
    }
  }

  private class GetAll extends AbstractAction {
    public GetAll() {
      putValue(NAME, "Get All");
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