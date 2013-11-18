package com.intellij.openapi.vcs;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

/**
 * @author Nadya Zabrodina
 */
public class VcsTestUtil {

  // TODO: option - create via IDEA or via java.io. In latter case no need in Project parameter.
  public static VirtualFile createFile(@NotNull Project project,
                                       @NotNull final VirtualFile parent,
                                       @NotNull final String name,
                                       @Nullable final String content) {
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
   *
   * @param parent Parent directory.
   * @param name   Name of the directory.
   * @return reference to the created or already existing directory.
   */
  public static VirtualFile createDir(@NotNull Project project, @NotNull final VirtualFile parent, @NotNull final String name) {
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
  public static <T, E> void assertEqualCollections(@NotNull Collection<T> actual,
                                                   @NotNull Collection<E> expected,
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

  private static <T, E> boolean contains(@NotNull Collection<T> collection,
                                         @NotNull E object,
                                         @NotNull EqualityChecker<T, E> equalityChecker) {
    for (T t : collection) {
      if (equalityChecker.areEqual(t, object)) {
        return true;
      }
    }
    return false;
  }

  private static <T, E> boolean contains2(@NotNull Collection<E> collection,
                                          @NotNull T object,
                                          @NotNull EqualityChecker<T, E> equalityChecker) {
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
      assertNotNull(String.format("description file not found in %s among %s", testDir, Arrays.toString(testDir.list())), descriptionFile);
      assertNotNull(String.format("config file file not found in %s among %s", testDir, Arrays.toString(testDir.list())), configFile);
      assertNotNull(String.format("result file file not found in %s among %s", testDir, Arrays.toString(testDir.list())), resultFile);

      String testName = FileUtil.loadFile(descriptionFile).split("\n")[0]; // description is in the first line of the desc-file
      data[i] = new Object[]{
        testName, configFile, resultFile
      };
    }
    return data;
  }

  @NotNull
  public static File getTestDataFolder() {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    return new File(pluginRoot, "testData");
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
    return ContainerUtil.map2List(relPaths, new Function<String, String>() {
      @Override
      public String fun(String s) {
        try {
          return FileUtil.toSystemIndependentName((new File(project.getBasePath() + "/" + s).getCanonicalPath()));
        }
        catch (IOException e) {
          e.printStackTrace();
          return "";
        }
      }
    });
  }
}
