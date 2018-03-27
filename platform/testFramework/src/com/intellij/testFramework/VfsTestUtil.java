// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author nik
 */
public class VfsTestUtil {
  public static final Key<String> TEST_DATA_FILE_PATH = Key.create("TEST_DATA_FILE_PATH");

  private VfsTestUtil() { }

  @NotNull
  public static VirtualFile createFile(@NotNull VirtualFile root, @NotNull String relativePath) {
    return createFile(root, relativePath, "");
  }

  @NotNull
  public static VirtualFile createFile(@NotNull VirtualFile root, @NotNull String relativePath, @NotNull String text) {
    return createFileOrDir(root, relativePath, text, false);
  }

  @NotNull
  public static VirtualFile createDir(@NotNull VirtualFile root, @NotNull String relativePath) {
    return createFileOrDir(root, relativePath, "", true);
  }

  @NotNull
  private static VirtualFile createFileOrDir(VirtualFile root, String relativePath, String text, boolean dir) {
    try {
      return WriteAction.compute(() -> {
        VirtualFile parent = root;
        for (String name : StringUtil.tokenize(PathUtil.getParentPath(relativePath), "/")) {
          VirtualFile child = parent.findChild(name);
          if (child == null || !child.isValid()) {
            child = parent.createChildDirectory(VfsTestUtil.class, name);
          }
          parent = child;
        }

        parent.getChildren();//need this to ensure that fileCreated event is fired

        String name = PathUtil.getFileName(relativePath);
        VirtualFile file;
        if (dir) {
          file = parent.createChildDirectory(VfsTestUtil.class, name);
        }
        else {
          file = parent.findChild(name);
          if (file == null) {
            file = parent.createChildData(VfsTestUtil.class, name);
          }
          VfsUtil.saveText(file, text);
          // update the document now, otherwise MemoryDiskConflictResolver will do it later at unexpected moment of time
          FileDocumentManager.getInstance().reloadFiles(file);
        }
        return file;
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void deleteFile(@NotNull VirtualFile file) {
    UtilKt.deleteFile(file);
  }

  public static void clearContent(@NotNull final VirtualFile file) {
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
    try {
      FileUtil.writeToFile(new File(filePath), actual);
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @NotNull
  public static VirtualFile findFileByCaseSensitivePath(@NotNull String absolutePath) {
    String vfsPath = FileUtil.toSystemIndependentName(absolutePath);
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(vfsPath);
    Assert.assertNotNull("file " + absolutePath + " not found", vFile);
    String realVfsPath = vFile.getPath();
    if (!SystemInfo.isFileSystemCaseSensitive && !vfsPath.equals(realVfsPath) &&
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
    if (!SystemInfo.isFileSystemCaseSensitive && !vfsPath.endsWith(vfsSuffixPath) &&
        StringUtil.endsWithIgnoreCase(vfsPath, vfsSuffixPath)) {
      String realSuffixPath = vfsPath.substring(vfsPath.length() - vfsSuffixPath.length());
      Assert.fail("Please correct case-sensitivity of path to prevent test failure on case-sensitive file systems:\n" +
                  "     path " + suffixPath + "\n" +
                  "real path " + realSuffixPath);
    }
  }

  @NotNull
  public static List<VFileEvent> getEvents(@NotNull Runnable action) {
    List<VFileEvent> allEvents = new ArrayList<>();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
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

  @NotNull
  public static List<String> print(@NotNull List<? extends VFileEvent> events) {
    return events.stream().map(VfsTestUtil::print).collect(Collectors.toList());
  }

  private static String print(VFileEvent e) {
    char type = '?';
    if (e instanceof VFileCreateEvent) type = 'C';
    else if (e instanceof VFileDeleteEvent) type = 'D';
    else if (e instanceof VFileContentChangeEvent) type = 'U';
    else if (e instanceof VFilePropertyChangeEvent) type = 'P';
    return type + " : " + e.getPath();
  }
}