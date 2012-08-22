package org.jetbrains.android.uipreview;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class VirtualFolderWrapper implements IAbstractFolder {
  private final Project myProject;
  private final VirtualFile myFolder;

  VirtualFolderWrapper(@NotNull Project project, @NotNull VirtualFile folder) {
    myProject = project;
    myFolder = folder;
  }

  @Override
  public boolean hasFile(String name) {
    return myFolder.findChild(name) != null;
  }

  @Nullable
  @Override
  public IAbstractFile getFile(String name) {
    final VirtualFile child = myFolder.findChild(name);
    return child != null && !child.isDirectory()
           ? new VirtualFileWrapper(myProject, child)
           : null;
  }

  @Nullable
  @Override
  public IAbstractFolder getFolder(String name) {
    final VirtualFile child = myFolder.findChild(name);
    return child != null && child.isDirectory()
           ? new VirtualFolderWrapper(myProject, child)
           : null;
  }

  @Override
  public IAbstractResource[] listMembers() {
    final VirtualFile[] children = myFolder.getChildren();
    final IAbstractResource[] result = new IAbstractResource[children.length];

    for (int i = 0; i < result.length; i++) {
      final VirtualFile child = children[i];

      result[i] = child.isDirectory()
                  ? new VirtualFolderWrapper(myProject, child)
                  : new VirtualFileWrapper(myProject, child);
    }
    return result;
  }

  @Override
  public String[] list(FilenameFilter filter) {
    final VirtualFile[] children = myFolder.getChildren();
    final List<String> result = new ArrayList<String>();

    for (VirtualFile child : children) {
      final String name = child.getName();

      if (filter == null || filter.accept(this, name)) {
        result.add(name);
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  public String getName() {
    return myFolder.getName();
  }

  @Override
  public String getOsLocation() {
    return FileUtil.toSystemDependentName(myFolder.getPath());
  }

  @Override
  public boolean exists() {
    return myFolder.exists();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final VirtualFile parent = myFolder.getParent();
    return parent != null
           ? new VirtualFolderWrapper(myProject, parent)
           : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VirtualFolderWrapper wrapper = (VirtualFolderWrapper)o;

    if (!myFolder.equals(wrapper.myFolder)) {
      return false;
    }
    if (!myProject.equals(wrapper.myProject)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + myFolder.hashCode();
    return result;
  }
}
