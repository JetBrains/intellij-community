/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.*;

public class VcsTestUtil {
  public static VirtualFile createFile(@NotNull Project project, @NotNull final VirtualFile parent, @NotNull final String name,
                                       @Nullable final String content) {
    try {
      return WriteCommandAction.writeCommandAction(project).compute(() -> {
        VirtualFile file = parent.createChildData(parent, name);
        if (content != null) {
          file.setBinaryContent(CharsetToolkit.getUtf8Bytes(content));
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
  public static VirtualFile findOrCreateDir(@NotNull final Project project, @NotNull final VirtualFile parent, @NotNull final String name) {
    try {
      return WriteCommandAction.writeCommandAction(project).compute(() -> {
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

  public static void renameFileInCommand(@NotNull Project project, @NotNull final VirtualFile file, @NotNull final String newName) {
    WriteCommandAction.writeCommandAction(project).run(() -> {
      try {
        file.rename(file, newName);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public static void deleteFileInCommand(@NotNull Project project, @NotNull final VirtualFile file) {
    WriteCommandAction.writeCommandAction(project).run(() -> {
      try {
        file.delete(file);
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public static void editFileInCommand(@NotNull Project project, @NotNull final VirtualFile file, @NotNull final String newContent) {
    assertTrue(file.isValid());
    file.getTimeStamp();
    WriteCommandAction.writeCommandAction(project).run(() -> {
      try {
        final long newTs = Math.max(System.currentTimeMillis(), file.getTimeStamp() + 1100);
        file.setBinaryContent(newContent.getBytes(), -1, newTs);
        final File file1 = new File(file.getPath());
        FileUtil.writeToFile(file1, newContent.getBytes());
        file.refresh(false, false);
        assertTrue(file1 + " / " + newTs, file1.setLastModified(newTs));
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  @NotNull
  public static VirtualFile copyFileInCommand(@NotNull Project project, @NotNull final VirtualFile file,
                                              @NotNull final VirtualFile newParent, @NotNull final String newName) {
    try {
      return WriteCommandAction.writeCommandAction(project).compute(() -> file.copy(file, newParent, newName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void moveFileInCommand(@NotNull  Project project, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) {
    try {
      WriteCommandAction.writeCommandAction(project).run(() -> file.move(file, newParent));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> void assertEqualCollections(@NotNull String message, @NotNull Collection<T> actual, @NotNull Collection<T> expected) {
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

  @NotNull
  public static String stringifyActualExpected(@NotNull Object actual, @NotNull Object expected) {
    return "\nExpected:\n" + expected + "\nActual:\n" + actual;
  }

  @NotNull
  public static String toAbsolute(@NotNull String relPath, @NotNull Project project) {
    new File(toAbsolute(Collections.singletonList(relPath), project).get(0)).mkdir();
    return toAbsolute(Collections.singletonList(relPath), project).get(0);
  }

  @NotNull
  public static List<String> toAbsolute(@NotNull Collection<String> relPaths, @NotNull final Project project) {
    return ContainerUtil.map2List(relPaths, s -> {
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
        ((TestVcsNotifier)VcsNotifier.getInstance(project)).getLastNotification();
      assertNotNull("No notification was shown", actualNotification);
      assertEquals("Notification has wrong title", expected.getTitle(), actualNotification.getTitle());
      assertEquals("Notification has wrong type", expected.getType(), actualNotification.getType());
      assertEquals("Notification has wrong content", adjustTestContent(expected.getContent()), actualNotification.getContent());
    }
  }

  // we allow more spaces and line breaks in tests to make them more readable.
  // After all, notifications display html, so all line breaks and extra spaces are ignored.
  private static String adjustTestContent(@NotNull String s) {
    StringBuilder res = new StringBuilder();
    String[] splits = s.split("\n");
    for (String split : splits) {
      res.append(split.trim());
    }

    return res.toString();
  }

  public static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/vcs-tests/testData";
  }
}
