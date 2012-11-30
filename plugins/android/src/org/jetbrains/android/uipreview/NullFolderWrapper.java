package org.jetbrains.android.uipreview;

import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.intellij.util.ArrayUtil;

/**
 * @author Eugene.Kudelevsky
 */
class NullFolderWrapper implements IAbstractFolder {
  @Override
  public boolean hasFile(String name) {
    return false;
  }

  @Override
  public IAbstractFile getFile(String name) {
    return null;
  }

  @Override
  public IAbstractFolder getFolder(String name) {
    return null;
  }

  @Override
  public IAbstractResource[] listMembers() {
    return new IAbstractResource[0];
  }

  @Override
  public String[] list(FilenameFilter filter) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getName() {
    return "stub_name";
  }

  @Override
  public String getOsLocation() {
    return "stub_os_location";
  }

  @Override
  public boolean exists() {
    return false;
  }

  @Override
  public IAbstractFolder getParentFolder() {
    return null;
  }

  @Override
  public boolean delete() {
    return false;
  }
}
