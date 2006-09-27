package com.intellij.cvsSupport2.application;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.netbeans.lib.cvsclient.admin.EntriesHandler;
import org.netbeans.lib.cvsclient.admin.Entry;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;

public class DeletedCVSDirectoryStorage {
  private File myRoot;
  public static final String CVS_ADMIN_DIR = CvsUtil.CVS;

  private final Collection<VirtualFile> myFilesToDelete = new HashSet<VirtualFile>();

  public DeletedCVSDirectoryStorage(File root) {
    myRoot = root;
  }

  public void saveCVSInfo(File directory) throws IOException {
    if (!directory.isDirectory()) return;
    File[] subdirectories = directory.listFiles(DirectoryFilter.getInstance());
    for (File subdirectory : subdirectories) {
      if (isAdminDir(subdirectory)) {
        FileUtil.copyDir(subdirectory, translatePath(subdirectory));
      }
      else {
        saveCVSInfo(subdirectory);
      }
    }
    File createdDirectory = translatePath(directory);
    if (doesNotContainFileEntry(createdDirectory)
        && doesNotContainDirectoriesExceptCvs(createdDirectory)) {
      FileUtil.delete(createdDirectory);
    }
  }

  private static boolean doesNotContainDirectoriesExceptCvs(File directory) {
    File[] files = directory.listFiles();
    if (files == null) return true;
    for (File file : files) {
      if (!isAdminDir(file)) return false;
    }
    return true;
  }

  private static boolean doesNotContainFileEntry(File directory) throws IOException {
    if (!containsEntriesFile(directory)) return true;
    EntriesHandler entriesHandler = new EntriesHandler(directory);
    entriesHandler.read(CvsApplicationLevelConfiguration.getCharset());
    Collection<Entry> entries = entriesHandler.getEntries().getEntries();
    for (Entry entry : entries) {
      if (!entry.isDirectory()) return false;
    }
    return true;
  }

  private static boolean containsEntriesFile(File directory) {
    return new File(new File(directory, CVS_ADMIN_DIR), CvsUtil.ENTRIES).isFile();
  }

  public static boolean isAdminDir(File subdirectory) {
    return subdirectory.getName().equals(CVS_ADMIN_DIR);
  }

  public static boolean isAdminDir(VirtualFile file) {
    if (!file.isDirectory()) return false;
    return file.getName().equals(CVS_ADMIN_DIR);
  }

  public void checkNeedForPurge(File file) {
    if (!file.isDirectory()) return;

    File[] subdirectories = file.listFiles(DirectoryFilter.getInstance());
    for (int i = 0; i < subdirectories.length; i++) {
      File subdirectory = subdirectories[i];
      checkNeedForPurge(subdirectory);
    }

    File savedCopy = translatePath(file);
    if (canDeleteSavedCopy(file, savedCopy)) FileUtil.delete(savedCopy);
  }

  public void purgeDirsWithNoEntries() throws IOException {
    purgeDirsWithoutFileEntries(myRoot);
  }

  private static boolean purgeDirsWithoutFileEntries(File root) throws IOException {
    File[] subdirectories = root.listFiles(DirectoryFilter.getInstance());
    if (subdirectories == null) return false;
    boolean canPurgeChildren = true;
    for (File subdirectory : subdirectories) {
      if (isAdminDir(subdirectory)) continue;
      boolean canPurgeChild = purgeDirsWithoutFileEntries(subdirectory);
      if (canPurgeChild) FileUtil.delete(subdirectory);
      canPurgeChildren &= canPurgeChild;
    }
    if (!canPurgeChildren) return false;
    return doesNotContainFileEntry(root);
  }

  public File translatePath(File file) {
    return translatePath(file.getAbsolutePath());
  }

  private File translatePath(String path) {
    return new File(myRoot, path.replace(':', '_'));
  }

  public void purge(File directory) {
    FileUtil.delete(directory);
  }

  public File alternatePath(File file) {
    return gotControlOver(file) ? translatePath(file) : file;
  }

  private boolean gotControlOver(File file) {
    return !file.exists() && (contains(file) || containsCvsDirFor(file));
  }

  public boolean contains(File file) {
    return translatePath(file).exists();
  }

  private boolean containsCvsDirFor(File file) {
    return (translatePath(new File(file.getParentFile(), CVS_ADMIN_DIR)).exists());
  }


  private static boolean canDeleteSavedCopy(File original, File copy) {
    File[] savedFiles = copy.listFiles();
    if (savedFiles == null) savedFiles = new File[0];
    for (File savedFile : savedFiles) {
      if (!new File(original, savedFile.getName()).exists()) return false;
    }
    return true;
  }

  public synchronized void deleteIfAdminDirCreated(final VirtualFile file) {
    if (isAdminDir(file)) {
      myFilesToDelete.add(file);
    }
    else if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        deleteIfAdminDirCreated(child);
      }
    }
  }

  public boolean restore(String path) throws IOException {
    File savedDir = translatePath(path);
    if (!savedDir.exists()) return false;
    FileUtil.copyDir(savedDir, new File(path));
    purge(savedDir);
    return true;
  }

  public DeleteHandler createDeleteHandler(Project project, CvsStorageComponent cvsStorageComponent) {
    return new DeleteHandler(this, project, cvsStorageComponent);
  }

  private static class DirectoryFilter implements FileFilter {
    private static FileFilter instance = new DirectoryFilter();

    public static FileFilter getInstance() {
      return instance;
    }

    public boolean accept(File pathname) {
      return pathname.isDirectory();
    }
  }

  public synchronized void sync() {
    try {
      if (!myFilesToDelete.isEmpty()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              for (VirtualFile file : myFilesToDelete) {
                file.delete(DeletedCVSDirectoryStorage.this);
              }
            }
            catch (final IOException e) {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  Messages.showErrorDialog(CvsBundle.message("message.error.cannot.delete.cvs.admin.directory"),
                                           CvsBundle.message("message.error.cannot.delete.cvs.admin.directory.title"));
                }
              });

            }
          }
        });
      }
    }
    finally {
      myFilesToDelete.clear();
    }
  }
}
