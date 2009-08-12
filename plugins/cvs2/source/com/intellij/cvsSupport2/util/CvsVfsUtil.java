package com.intellij.cvsSupport2.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * author: lesya
 */
public class CvsVfsUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.util.CvsVfsUtil");

  public static VirtualFile findFileByPath(final String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  public static VirtualFile findChild(final VirtualFile file, final String name) {
    if (file == null) {
      return LocalFileSystem.getInstance().findFileByIoFile(new File(name));
    }
    else {
      return file.findChild(name);
    }
  }

  public static VirtualFile getParentFor(final File file) {
    return findFileByIoFile(file.getParentFile());
  }

  public static File getFileFor(final VirtualFile file) {
    if (file == null) return null;
    return new File(file.getPath());
  }

  public static File getFileFor(final VirtualFile parent, String name) {
    if (parent == null) {
      return new File(name);
    }
    else {
      return new File(parent.getPath(), name);
    }
  }

  public static VirtualFile findFileByIoFile(final File file) {
    if (file == null) return null;
    return LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  public static VirtualFile refreshAndFindFileByIoFile(final File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  public static VirtualFile[] getChildrenOf(final VirtualFile directory) {
    return directory.isValid() ? directory.getChildren() : null;
  }

  public static long getTimeStamp(final VirtualFile file) {
    return file.getTimeStamp();
  }

  public static boolean isWritable(final VirtualFile virtualFile) {
    return virtualFile.isWritable();
  }

  public static String getPresentablePathFor(final VirtualFile root) {
    return root.getPresentableUrl();
  }

  public static VirtualFile refreshAndfFindChild(VirtualFile parent, String fileName) {
    return refreshAndFindFileByIoFile(new File(CvsVfsUtil.getFileFor(parent), fileName));
  }
}
