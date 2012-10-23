/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * @author Kirill Likhodedov
 */
public class GitTestUtil {

  /**
   * <p>Creates file structure for given paths. Path element should be a relative (from project root)
   * path to a file or a directory. All intermediate paths will be created if needed.
   * To create a dir without creating a file pass "dir/" as a parameter.</p>
   * <p>Usage example:
   * <code>createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt", "anotherdir/");</code></p>
   * <p>This will create files a.txt and b.txt in the project dir, create directories dir, dir/subdir and anotherdir,
   * and create file c.txt in dir and d.txt in dir/subdir.</p>
   * <p>Note: use forward slash to denote directories, even if it is backslash that separates dirs in your system.</p>
   * <p>All files are populated with "initial content" string.</p>
   */
  public static Map<String, VirtualFile> createFileStructure(Project project, GitTestRepository repo, String... paths) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();

    for (String path : paths) {
      String[] pathElements = path.split("/");
      boolean lastIsDir = path.endsWith("/");
      VirtualFile currentParent = repo.getVFRootDir();
      for (int i = 0; i < pathElements.length-1; i++) {
        currentParent = createDir(project, currentParent, pathElements[i]);
      }

      String lastElement = pathElements[pathElements.length-1];
      currentParent = lastIsDir ? createDir(project, currentParent, lastElement) : createFile(project, currentParent, lastElement, "content" + Math.random());
      result.put(path, currentParent);
    }
    return result;
  }

  // TODO: option - create via IDEA or via java.io. In latter case no need in Project parameter.
  public static VirtualFile createFile(Project project, final VirtualFile parent, final String name, @Nullable final String content) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        try {
          VirtualFile file = parent.createChildData(this, name);
          if (content != null) {
            file.setBinaryContent(CharsetToolkit.getUtf8Bytes(content));
          }
          result.set(file);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
  }

  /**
   * TODO: option - create via IDEA or via java.io. In latter case no need in Project parameter.
   * Creates directory inside a write action and returns the resulting reference to it.
   * If the directory already exists, does nothing.
   * @param parent Parent directory.
   * @param name   Name of the directory.
   * @return reference to the created or already existing directory.
   */
  public static VirtualFile createDir(Project project, final VirtualFile parent, final String name) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        try {
          VirtualFile dir = parent.findChild(name);
          if (dir == null) {
            dir = parent.createChildDirectory(this, name);
          }
          result.set(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
  }

  /**
   * Testng compares by iterating over 2 collections, but it won't work for sets which may have different order.
   */
  public static <T> void assertEqualCollections(@NotNull Collection<T> actual, @NotNull Collection<T> expected) {
    if (actual.size() != expected.size()) {
      fail("Collections don't have the same size. " + stringifyActualExpected(actual, expected));
    }
    for (T act : actual) {
      if (!expected.contains(act)) {
        fail("Unexpected object " + act + stringifyActualExpected(actual, expected));
      }
    }
    // backwards is needed for collections which may contain duplicates, e.g. Lists.
    for (T exp : expected) {
      if (!actual.contains(exp)) {
        fail("Object " + exp + " not found in actual collection." + stringifyActualExpected(actual, expected));
      }
    }
  }

  /**
   * Testng compares by iterating over 2 collections, but it won't work for sets which may have different order.
   */
  public static <T, E> void assertEqualCollections(@NotNull Collection<T> actual, @NotNull Collection<E> expected, @NotNull EqualityChecker<T, E> equalityChecker) {
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

  private static <T, E> boolean contains(@NotNull Collection<T> collection, @NotNull E object, @NotNull EqualityChecker<T, E> equalityChecker) {
    for (T t : collection) {
      if (equalityChecker.areEqual(t, object)) {
        return true;
      }
    }
    return false;
  }
  
  private static <T, E> boolean contains2(Collection<E> collection, T object, EqualityChecker<T, E> equalityChecker) {
    for (E e : collection) {
      if (equalityChecker.areEqual(object, e)) {
        return true;
      }
    }
    return false;
  }

  public static Object[][] loadConfigData(@NotNull File dataFolder) throws IOException {
    File[] tests = dataFolder.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !name.startsWith(".");
      }
    });
    Object[][] data = new Object[tests.length][];
    for (int i = 0; i < tests.length; i++) {
      File testDir = tests[i];
      File descriptionFile = null;
      File configFile = null;
      File resultFile = null;
      for (File file : testDir.listFiles()) {
        if (file.getName().endsWith("_desc.txt")) {
          descriptionFile = file;
        }
        else if (file.getName().endsWith("_config.txt")) {
          configFile = file;
        }
        else if (file.getName().endsWith("_result.txt")) {
          resultFile = file;
        }
      }
      assertNotNull(descriptionFile, String.format("description file not found in %s among %s", testDir, Arrays.toString(testDir.list())));
      assertNotNull(configFile, String.format("config file file not found in %s among %s", testDir, Arrays.toString(testDir.list())));
      assertNotNull(resultFile, String.format("result file file not found in %s among %s", testDir, Arrays.toString(testDir.list())));

      String testName = FileUtil.loadFile(descriptionFile).split("\n")[0]; // description is in the first line of the desc-file
      data[i] = new Object[]{
        testName, configFile, resultFile
      };
    }
    return data;
  }

  public static File getTestDataFolder() {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    return new File(pluginRoot, "testData");
  }

  // for testing purposes we test the behavior of windows and unix gits on both platforms
  // this method sets SystemInfo.isWindows to whatever we want
  public static void setWindows(boolean windows) throws NoSuchFieldException, IllegalAccessException {
    Field win = SystemInfo.class.getDeclaredField("isWindows");
    win.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(win, win.getModifiers() & ~Modifier.FINAL);

    win.set(null, windows);

    assertEquals(SystemInfo.isWindows, windows);
  }

  public interface EqualityChecker<T, E> {
    boolean areEqual(T actual, E expected);
  }

  @NotNull
  public static String stringifyActualExpected(@NotNull Object actual, @NotNull Object expected) {
    return "\nExpected:\n" + expected + "\nActual:\n" + actual;
  }

}
