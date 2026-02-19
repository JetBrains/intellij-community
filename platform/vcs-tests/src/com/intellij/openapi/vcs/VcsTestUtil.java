// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public final class VcsTestUtil {
  public static VirtualFile createFile(@NotNull Project project, final @NotNull VirtualFile parent, final @NotNull String name,
                                       final @Nullable String content) {
    try {
      return WriteCommandAction.writeCommandAction(project)
        .withName("VcsTestUtil CreateFile") //NON-NLS
        .compute(() -> {
          VirtualFile file = parent.createChildData(parent, name);
          if (content != null) {
            file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
          }
          return file;
        });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates directory inside a write action and returns the resulting reference to it.
   * If the directory already exists, does nothing.
   *
   * @param parent Parent directory.
   * @param name   Name of the directory.
   * @return reference to the created or already existing directory.
   */
  public static VirtualFile findOrCreateDir(final @NotNull Project project, final @NotNull VirtualFile parent, final @NotNull String name) {
    try {
      return WriteCommandAction.writeCommandAction(project)
        .withName("VcsTestUtil FindOrCreateDir") //NON-NLS
        .compute(() -> {
          VirtualFile dir = parent.findChild(name);
          if (dir == null) {
            dir = parent.createChildDirectory(parent, name);
          }
          return dir;
        });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void renameFileInCommand(@NotNull Project project, final @NotNull VirtualFile file, final @NotNull String newName) {
    WriteCommandAction.writeCommandAction(project)
      .withName("VcsTestUtil RenameFileInCommand") //NON-NLS
      .run(() -> {
        try {
          file.rename(file, newName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
  }

  public static void deleteFileInCommand(@NotNull Project project, final @NotNull VirtualFile file) {
    WriteCommandAction.writeCommandAction(project)
      .withName("VcsTestUtil DeleteFileInCommand") //NON-NLS
      .run(() -> {
        try {
          file.delete(file);
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
  }

  public static void editFileInCommand(@NotNull Project project, final @NotNull VirtualFile file, final @NotNull String newContent) {
    assertTrue(file.isValid());
    file.getTimeStamp();
    WriteCommandAction.writeCommandAction(project)
      .withName("VcsTestUtil EditFileInCommand") //NON-NLS
      .run(() -> {
        try {
          final long newTs = Math.max(System.currentTimeMillis(), file.getTimeStamp() + 1100);
          file.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8), -1, newTs);
          final File file1 = new File(file.getPath());
          FileUtil.writeToFile(file1, newContent.getBytes(StandardCharsets.UTF_8));
          file.refresh(false, false);
          assertTrue(file1 + " / " + newTs, file1.setLastModified(newTs));
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
  }

  public static @NotNull VirtualFile copyFileInCommand(@NotNull Project project, final @NotNull VirtualFile file,
                                                       final @NotNull VirtualFile newParent, final @NotNull String newName) {
    try {
      return WriteCommandAction.writeCommandAction(project)
        .withName("VcsTestUtil CopyFileInCommand") //NON-NLS
        .compute(() -> file.copy(file, newParent, newName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void moveFileInCommand(@NotNull Project project, final @NotNull VirtualFile file, final @NotNull VirtualFile newParent) {
    // Workaround for IDEA-182560, try to wait until ongoing refreshes are finished
    VfsUtil.markDirty(true, true, file);
    var semaphore = new Semaphore(1);
    LocalFileSystem.getInstance().refreshFiles(List.of(file), true, true, () -> semaphore.up());
    semaphore.waitFor();

    try {
      WriteCommandAction.writeCommandAction(project)
        .withName("VcsTestUtil MoveFileInCommand") //NON-NLS
        .run(() -> file.move(file, newParent));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> void assertEqualCollections(@NotNull String message,
                                                @NotNull Collection<? extends T> actual,
                                                @NotNull Collection<? extends T> expected) {
    if (!StringUtil.isEmptyOrSpaces(message) && !message.endsWith(":") && !message.endsWith(": ")) {
      message += ": ";
    }
    if (actual.size() != expected.size()) {
      fail(message + "Collections don't have the same size. " + stringifyActualExpected(actual, expected));
    }
    for (T act : actual) {
      if (!expected.contains(act)) {
        fail(message + "Unexpected object " + act + stringifyActualExpected(actual, expected));
      }
    }
    // backwards is needed for collections which may contain duplicates, e.g. Lists.
    for (T exp : expected) {
      if (!actual.contains(exp)) {
        fail(message + "Object " + exp + " not found in actual collection." + stringifyActualExpected(actual, expected));
      }
    }
  }

  public static <T> void assertEqualCollections(@NotNull Collection<T> actual, @NotNull Collection<T> expected) {
    assertEqualCollections("", actual, expected);
  }

  /**
   * Testng compares by iterating over 2 collections, but it won't work for sets which may have different order.
   */
  public static <T, E> void assertEqualCollections(@NotNull Collection<? extends T> actual,
                                                   @NotNull Collection<? extends E> expected,
                                                   @NotNull EqualityChecker<T, E> equalityChecker) {
    if (actual.size() != expected.size()) {
      fail("Collections don't have the same size. " + stringifyActualExpected(actual, expected));
    }
    for (T act : actual) {
      if (!contains2(expected, act, equalityChecker)) {
        fail("Unexpected object " + act + stringifyActualExpected(actual, expected));
      }
    }
    // backwards is needed for collections which may contain duplicates, e.g. Lists.
    for (E exp : expected) {
      if (!contains(actual, exp, equalityChecker)) {
        fail("Object " + exp + " not found in actual collection." + stringifyActualExpected(actual, expected));
      }
    }
  }

  private static <T, E> boolean contains(@NotNull Collection<? extends T> collection,
                                         @NotNull E object,
                                         @NotNull EqualityChecker<T, E> equalityChecker) {
    for (T t : collection) {
      if (equalityChecker.areEqual(t, object)) {
        return true;
      }
    }
    return false;
  }

  private static <T, E> boolean contains2(@NotNull Collection<? extends E> collection,
                                          @NotNull T object,
                                          @NotNull EqualityChecker<T, E> equalityChecker) {
    for (E e : collection) {
      if (equalityChecker.areEqual(object, e)) {
        return true;
      }
    }
    return false;
  }

  public interface EqualityChecker<T, E> {
    boolean areEqual(T actual, E expected);
  }

  public static @NotNull String stringifyActualExpected(@NotNull Object actual, @NotNull Object expected) {
    return "\nExpected:\n" + expected + "\nActual:\n" + actual;
  }

  public static @NotNull String toAbsolute(@NotNull String relPath, @NotNull Project project) {
    new File(toAbsolute(Collections.singletonList(relPath), project).get(0)).mkdir();
    return toAbsolute(Collections.singletonList(relPath), project).get(0);
  }

  public static @NotNull List<String> toAbsolute(@NotNull Collection<String> relPaths, final @NotNull Project project) {
    return ContainerUtil.map(relPaths, s -> {
      try {
        return FileUtil.toSystemIndependentName((new File(project.getBasePath() + "/" + s).getCanonicalPath()));
      }
      catch (IOException e) {
        e.printStackTrace();
        return "";
      }
    });
  }

  public static void assertNotificationShown(@NotNull Project project, @Nullable Notification expected) {
    if (expected != null) {
      Notification actualNotification =
        ((TestVcsNotifier)VcsNotifier.getInstance(project)).findExpectedNotification(expected);
      assertNotNull("No notification was shown", actualNotification);
    }
  }

  public static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/vcs-tests/testData";
  }
}
