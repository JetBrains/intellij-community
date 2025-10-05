// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class VfsTestUtil {
  public static final Key<String> TEST_DATA_FILE_PATH = Key.create("TEST_DATA_FILE_PATH");

  private VfsTestUtil() { }

  /**
   * Invokes VirtualFileManager.syncRefresh() and waits until indexes are ready after VFS refresh
   */
  public static void syncRefresh() {
    Application app = ApplicationManager.getApplication();
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    if (app.isWriteAccessAllowed()) {
      virtualFileManager.syncRefresh();
    }
    else if (app.isDispatchThread()) {
      WriteAction.compute(virtualFileManager::syncRefresh);
    }
    else {
      app.invokeAndWait(() -> {
        WriteAction.compute(virtualFileManager::syncRefresh);
      });
    }

    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
  }

  public static @NotNull VirtualFile createFile(@NotNull VirtualFile root, @NotNull String relativePath) {
    return createFile(root, relativePath, (byte[])null);
  }

  public static @NotNull VirtualFile createFile(@NotNull VirtualFile root, @NotNull String relativePath, @Nullable String text) {
    try {
      return createFileOrDir(root, relativePath, text == null ? null : VfsUtil.toByteArray(root, text), false);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static @NotNull VirtualFile createFile(@NotNull VirtualFile root, @NotNull String relativePath, byte @Nullable [] data) {
    return createFileOrDir(root, relativePath, data, false);
  }

  public static @NotNull VirtualFile createDir(@NotNull VirtualFile root, @NotNull String relativePath) {
    return createFileOrDir(root, relativePath, null, true);
  }

  private static @NotNull VirtualFile createFileOrDir(VirtualFile root, String relativePath, byte @Nullable [] data, boolean dir) {
    try {
      return WriteAction.computeAndWait(() -> {
        VirtualFile parent = root;
        for (String name : StringUtil.tokenize(PathUtil.getParentPath(relativePath), "/")) {
          VirtualFile child = parent.findChild(name);
          if (child == null || !child.isValid()) {
            child = parent.createChildDirectory(VfsTestUtil.class, name);
          }
          parent = child;
        }

        parent.getChildren();  // to ensure that the "file created" event is fired

        String name = PathUtil.getFileName(relativePath);
        VirtualFile file;
        if (dir) {
          file = parent.createChildDirectory(VfsTestUtil.class, name);
        }
        else {
          FileDocumentManager manager = FileDocumentManager.getInstance();
          file = parent.findChild(name);
          if (file == null) {
            file = parent.createChildData(VfsTestUtil.class, name);
          }
          else {
            Document document = manager.getCachedDocument(file);
            if (document != null) manager.saveDocument(document);  // save changes to prevent possible conflicts
          }
          if (data != null) {
            file.setBinaryContent(data);
          }
          manager.reloadFiles(file);  // update the document now to prevent `MemoryDiskConflictResolver` from kicking in later
        }
        return file;
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void deleteFile(@NotNull VirtualFile file) {
    try {
      // requestor must be notnull (for GlobalUndoTest)
      WriteAction.runAndWait(() -> file.delete(file));
    }
    catch (Throwable throwable) {
      ExceptionUtil.rethrow(throwable);
    }
  }

  public static void clearContent(final @NotNull VirtualFile file) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        VfsUtil.saveText(file, "");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public static void overwriteTestData(@NotNull String filePath, @NotNull String actual) {
    overwriteTestData(filePath, actual, false);
  }

  public static void overwriteTestData(@NotNull String filePath, @NotNull String actual, boolean preserveSpaces) {
    try {
      File file = new File(filePath);
      if (preserveSpaces) {
        try {
          actual = preserveSpacesFromFile(file, actual);
        }
        catch (Throwable e) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println("Failed to preserve spaces: " + e.getMessage());
        }
      }
      FileUtil.writeToFile(file, actual);
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String preserveSpacesFromFile(@NotNull File file, @NotNull String actual) throws IOException {
    if (!file.exists()) {
      return actual;
    }
    String existing = FileUtil.loadFile(file, StandardCharsets.UTF_8);
    int eLen = existing.length();
    int lead = 0;
    while (lead < eLen && Character.isWhitespace(existing.charAt(lead))) {
      ++lead;
    }
    int trail = eLen;
    if (lead != eLen) {
      while (trail > 0 && Character.isWhitespace(existing.charAt(trail - 1))) {
        --trail;
      }
    }
    actual = existing.substring(0, lead) + actual.trim() + existing.substring(trail, eLen);
    return actual;
  }

  public static @NotNull VirtualFile findFileByCaseSensitivePath(@NotNull String absolutePath) {
    String vfsPath = FileUtil.toSystemIndependentName(absolutePath);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(vfsPath);
    Assert.assertNotNull("file " + absolutePath + " not found", vFile);
    String realVfsPath = vFile.getPath();
    if (!vFile.isCaseSensitive() && !vfsPath.equals(realVfsPath) &&
        vfsPath.equalsIgnoreCase(realVfsPath)) {
      Assert.fail("Please correct case-sensitivity of path to prevent test failure on case-sensitive file systems:\n" +
                  "     path " + vfsPath + "\n" +
                  "real path " + realVfsPath);
    }
    return vFile;
  }

  public static void assertFilePathEndsWithCaseSensitivePath(@NotNull VirtualFile file, @NotNull String suffixPath) {
    String vfsSuffixPath = FileUtil.toSystemIndependentName(suffixPath);
    String vfsPath = file.getPath();
    if (!file.isCaseSensitive() && !vfsPath.endsWith(vfsSuffixPath) &&
        StringUtil.endsWithIgnoreCase(vfsPath, vfsSuffixPath)) {
      String realSuffixPath = vfsPath.substring(vfsPath.length() - vfsSuffixPath.length());
      Assert.fail("Please correct case-sensitivity of path to prevent test failure on case-sensitive file systems:\n" +
                  "     path " + suffixPath + "\n" +
                  "real path " + realSuffixPath);
    }
  }

  public static @NotNull List<VFileEvent> getEvents(@NotNull Runnable action) {
    List<VFileEvent> allEvents = Collections.synchronizedList(new ArrayList<>());

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        allEvents.addAll(events);
      }
    });
    try {
      action.run();
    }
    finally {
      connection.disconnect();
    }

    return allEvents;
  }

  public static @Unmodifiable @NotNull List<String> print(@NotNull List<? extends VFileEvent> events) {
    return ContainerUtil.map(events, VfsTestUtil::print);
  }

  private static String print(VFileEvent e) {
    char type = '?';
    if (e instanceof VFileCreateEvent) type = 'C';
    else if (e instanceof VFileDeleteEvent) type = 'D';
    else if (e instanceof VFileContentChangeEvent) type = 'U';
    else if (e instanceof VFilePropertyChangeEvent) type = 'P';
    return type + " : " + e.getPath();
  }

  public static void waitForFileWatcher() {
    if (LocalFileSystem.getInstance() instanceof LocalFileSystemImpl impl) {
      var watcher = impl.getFileWatcher();
      var stopAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
      while (watcher.isSettingRoots() && System.nanoTime() < stopAt) {
        TimeoutUtil.sleep(10);
      }
    }
  }
}
