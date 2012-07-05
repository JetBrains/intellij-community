package org.jetbrains.android.util;

import com.android.io.FolderWrapper;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class BufferingFolderWrapper implements IAbstractFolder {
  private final File myFile;
  private final IAbstractFolder myDelegate;

  public BufferingFolderWrapper(@NotNull File file) {
    myFile = file;
    myDelegate = new FolderWrapper(file);
  }

  @Override
  public boolean hasFile(String name) {
    return myDelegate.hasFile(name);
  }

  @Override
  public IAbstractFile getFile(String name) {
    return new BufferingFileWrapper(new File(myFile, name));
  }

  @Override
  public IAbstractFolder getFolder(String name) {
    return new BufferingFolderWrapper(new File(myFile, name));
  }

  @Override
  public IAbstractResource[] listMembers() {
    final File[] files = myFile.listFiles();
    
    if (files == null) {
      return new IAbstractResource[0];
    }
    final IAbstractResource[] result = new IAbstractResource[files.length];

    for (int i = 0; i < result.length; i++) {
      final File file = files[i];
      
      result[i] = file.isFile() 
                  ? new BufferingFileWrapper(file)
                  : new BufferingFolderWrapper(file);
    }
    return result;
  }

  @Override
  public String[] list(FilenameFilter filter) {
    return myDelegate.list(filter);
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getOsLocation() {
    return myFile.getAbsolutePath();
  }

  @Override
  public boolean exists() {
    return myFile.isDirectory();
  }

  @Nullable
  @Override
  public IAbstractFolder getParentFolder() {
    final File parent = myFile.getParentFile();
    return parent != null ? new BufferingFolderWrapper(parent) : null;
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BufferingFolderWrapper wrapper = (BufferingFolderWrapper)o;

    return FileUtil.filesEqual(myFile, wrapper.myFile);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(myFile);
  }
}
