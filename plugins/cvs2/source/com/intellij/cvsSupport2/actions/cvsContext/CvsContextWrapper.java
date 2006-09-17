package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsContextWrapper implements CvsContext {

  private final VcsContext myVcsContext;
  private final DataContext myContext;

  private CvsContextWrapper(AnActionEvent actionEvent, final VcsContext vcsContext) {
    myContext = actionEvent.getDataContext();
    myVcsContext = vcsContext;
  }

  public static CvsContext createCachedInstance(AnActionEvent event) {
    return new CachedCvsContext(new CvsContextWrapper(event, PeerFactory.getInstance().getVcsContextFactory().createCachedContextOn(event)));
  }

  public static CvsContext createInstance(AnActionEvent event) {
    return new CvsContextWrapper(event, PeerFactory.getInstance().getVcsContextFactory().createContextOn(event));
  }

  public boolean cvsIsActive() {
    Project project = getProject();
    if (project == null) return false;
    return ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(CvsVcs2.getInstance(project), getSelectedFiles());
  }


  public Collection<String> getDeletedFileNames() {
    return (Collection<String>)myContext.getData(CvsDataConstants.DELETED_FILE_NAMES);
  }

  public String getFileToRestore() {
    return (String)myContext.getData(CvsDataConstants.FILE_TO_RESTORE);
  }

  public File getSomeSelectedFile() {
    File[] selectedIOFiles = getSelectedIOFiles();
    VirtualFile[] selectedFiles = getSelectedFiles();

    if (selectedFiles == null || selectedFiles.length == 0) {
      return myVcsContext.getSelectedIOFile();
    }

    if (selectedIOFiles == null || selectedIOFiles.length == 0) {
      return CvsVfsUtil.getFileFor(getSelectedFile());
    }

    return selectedIOFiles[0];
  }

  public CvsLightweightFile getCvsLightweightFile() {
    return (CvsLightweightFile)myContext.getData(CvsDataConstants.CVS_LIGHT_FILE);
  }

  public CvsLightweightFile[] getSelectedLightweightFiles() {
    CvsLightweightFile[] files = (CvsLightweightFile[])myContext.getData(CvsDataConstants.CVS_LIGHT_FILES);
    if (files != null && files.length > 0) return files;
    CvsLightweightFile file = getCvsLightweightFile();
    if (file != null) {
      return new CvsLightweightFile[]{file};
    }
    else {
      return null;
    }
  }

  public CvsEnvironment getEnvironment() {
    return (CvsEnvironment)myContext.getData(CvsDataConstants.CVS_ENVIRONMENT);
  }

  public Collection<AddedFileInfo> getAllFilesToAdd() {
    return (Collection<AddedFileInfo>)myContext.getData(CvsDataConstants.FILES_TO_ADD);
  }

  public Project getProject() {
    return myVcsContext.getProject();
  }

  public VirtualFile getSelectedFile() {
    return myVcsContext.getSelectedFile();
  }

  public VirtualFile[] getSelectedFiles() {
    return myVcsContext.getSelectedFiles();
  }

  public Editor getEditor() {
    return myVcsContext.getEditor();
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return myVcsContext.getSelectedFilesCollection();
  }

  public File[] getSelectedIOFiles() {
    return myVcsContext.getSelectedIOFiles();
  }

  public int getModifiers() {
    return myVcsContext.getModifiers();
  }

  public Refreshable getRefreshableDialog() {
    return myVcsContext.getRefreshableDialog();
  }

  public String getPlace() {
    return myVcsContext.getPlace();
  }

  public PsiElement getPsiElement() {
    return myVcsContext.getPsiElement();
  }

  public File getSelectedIOFile() {
    return myVcsContext.getSelectedIOFile();
  }

  public FilePath[] getSelectedFilePaths() {
    return myVcsContext.getSelectedFilePaths();
  }

  public FilePath getSelectedFilePath() {
    return myVcsContext.getSelectedFilePath();
  }

  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return myVcsContext.getSelectedChangeLists();
  }

  @Nullable
  public Change[] getSelectedChanges() {
    return myVcsContext.getSelectedChanges();
  }
}
