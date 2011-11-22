package org.jetbrains.android.uipreview;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.StreamException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Eugene.Kudelevsky
 */
class VirtualFileWrapper implements IAbstractFile {
  private final Project myProject;
  private final VirtualFile myFile;

  VirtualFileWrapper(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myProject = project;
  }

  @Override
  public InputStream getContents() throws StreamException {
    final String content = getFileContent();
    return new ByteArrayInputStream(content.getBytes());
  }

  @NotNull
  private String getFileContent() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        if (!myFile.isValid()) {
          return "";
        }

        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
        return psiFile != null ? psiFile.getText() : "";
      }
    });
  }

  @Override
  public void setContents(InputStream source) throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream getOutputStream() throws StreamException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PreferredWriteMode getPreferredWriteMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getOsLocation() {
    return FileUtil.toSystemDependentName(myFile.getPath());
  }

  @Override
  public boolean exists() {
    return myFile.exists();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final VirtualFile parent = myFile.getParent();
    return parent != null ? new VirtualFolderWrapper(myProject, parent) : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }
}
