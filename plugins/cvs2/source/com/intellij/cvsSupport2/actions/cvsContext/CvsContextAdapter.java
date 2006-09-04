package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsContextAdapter implements CvsContext{
  public Project getProject() {
    return null;
  }

  public boolean cvsIsActive() {
    return false;
  }

  @Nullable
  public VirtualFile getSelectedFile() {
    return null;
  }

  public VirtualFile[] getSelectedFiles() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public Refreshable getRefreshableDialog() {
    return null;
  }

  public Collection<String> getDeletedFileNames() {
    return null;
  }

  public String getFileToRestore() {
    return null;
  }

  public Editor getEditor() {
    return null;
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return null;
  }

  public File getSomeSelectedFile() {
    return null;
  }

  public File[] getSelectedIOFiles() {
    return new File[0];
  }

  public int getModifiers() {
    return 0;
  }

  public CvsLightweightFile getCvsLightweightFile() {
    return null;
  }

  public CvsEnvironment getEnvironment() {
    return null;
  }

  public Collection<AddedFileInfo> getAllFilesToAdd() {
    return null;
  }

  public CvsLightweightFile[] getSelectedLightweightFiles() {
    return new CvsLightweightFile[0];
  }

  public String getPlace() {
    return null;
  }

  public PsiElement getPsiElement() {
    return null;
  }

  public File getSelectedIOFile() {
    return null;
  }

  public FilePath[] getSelectedFilePaths() {
    return null;
  }

  public FilePath getSelectedFilePath() {
    return null;
  }
}
