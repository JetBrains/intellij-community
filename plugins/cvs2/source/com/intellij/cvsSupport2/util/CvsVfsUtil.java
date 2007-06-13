package com.intellij.cvsSupport2.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * author: lesya
 */
public class CvsVfsUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.util.CvsVfsUtil");

  public static VirtualFile findFileByPath(final String path) {
    final VirtualFile[] result = new VirtualFile[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = LocalFileSystem.getInstance().findFileByPath(path);
      }
    });
    return result[0];
  }

  public static VirtualFile findChild(final VirtualFile file, final String name) {
    final VirtualFile[] result = new VirtualFile[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (file == null) {
          result[0] = LocalFileSystem.getInstance().findFileByIoFile(new File(name));
        }
        else {
          result[0] = file.findChild(name);
        }
      }
    });
    return result[0];
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
    final VirtualFile[] result = new VirtualFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar,
                                                                                                '/'));
      }
    });

    if (result[0] != null) return result[0];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    });
    return result[0];
  }

  public static VirtualFile refreshAndFindFileByIoFile(final File file) {
    if (file == null) return null;
    final VirtualFile[] result = new VirtualFile[1];
    Runnable action = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            result[0] = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
          }
        });

      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      action.run();
    }
    else {
      try {
        ApplicationManager.getApplication().invokeAndWait(action, ModalityState.defaultModalityState());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return result[0];
  }

  public static VirtualFile[] getChildrenOf(final VirtualFile directory) {
    final VirtualFile[][] result = new VirtualFile[1][];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = directory.getChildren();
      }
    });
    return result[0];
  }

  public static long getTimeStamp(final VirtualFile file) {
    final long[] result = new long[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = file.getTimeStamp();
      }
    });
    return result[0];
  }

  public static boolean isWritable(final VirtualFile virtualFile) {
    final boolean[] result = new boolean[]{false};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result[0] = virtualFile.isWritable();
      }
    });
    return result[0];

  }

  public static String getPresentablePathFor(final VirtualFile root) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return root.getPresentableUrl();
      }
    });
  }

  public static VirtualFile refreshAndfFindChild(VirtualFile parent, String fileName) {
    return refreshAndFindFileByIoFile(new File(CvsVfsUtil.getFileFor(parent), fileName));
  }
}
