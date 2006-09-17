package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CachedCvsContext implements CvsContext{
  private final boolean myIsActive;
  private final Collection<String> myDeletedFileNames;
  private final String myFileToRestore;
  private final File mySomeSelectedFile;
  private final CvsLightweightFile myCvsLightweightFile;
  private final CvsLightweightFile[] myCvsLightweightFiles;
  private final CvsEnvironment myEnvironment;
  private final Collection<AddedFileInfo> myFilesToAdd;

  private final VcsContext myVcsContext;

  public CachedCvsContext(CvsContext baseContext){
    myIsActive = baseContext.cvsIsActive();
    myDeletedFileNames = baseContext.getDeletedFileNames();
    myFileToRestore = baseContext.getFileToRestore();
    mySomeSelectedFile = baseContext.getSomeSelectedFile();
    myCvsLightweightFile = baseContext.getCvsLightweightFile();
    myEnvironment = baseContext.getEnvironment();
    myFilesToAdd = baseContext.getAllFilesToAdd();
    myCvsLightweightFiles = baseContext.getSelectedLightweightFiles();
    myVcsContext = baseContext;
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

  public boolean cvsIsActive() {
    return myIsActive;
  }

  public Collection<String> getDeletedFileNames() {
    return myDeletedFileNames;
  }

  public String getFileToRestore() {
    return myFileToRestore;
  }

  public File getSomeSelectedFile() {
    return mySomeSelectedFile;
  }

  public CvsLightweightFile getCvsLightweightFile() {
    return myCvsLightweightFile;
  }

  public CvsEnvironment getEnvironment() {
    return myEnvironment;
  }

  public Collection<AddedFileInfo> getAllFilesToAdd() {
    return myFilesToAdd;
  }

  public CvsLightweightFile[] getSelectedLightweightFiles() {
    return myCvsLightweightFiles;
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
