// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_IN_EDT;

public abstract class TMHInstrumenterTestBase extends UsefulTestCase {

  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/instrumenter/";
  private static final String TESTING_BACKGROUND_THREAD_NAME = "TESTING_BACKGROUND_THREAD";

  private final String dependencyPath;
  private final boolean useThreadingAssertions;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected TMHInstrumenterTestBase(String dependencyPath, boolean useThreadingAssertions) {
    this.dependencyPath = dependencyPath;
    this.useThreadingAssertions = useThreadingAssertions;
  }

  final void doEdtTest() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    invokeMethod(testClass.aClass);
    assertThrows(Throwable.class, MUST_EXECUTE_IN_EDT, () -> executeInBackground(() -> invokeMethod(testClass.aClass)));
  }

  final @NotNull TestClass getInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertTrue(testClass.isInstrumented);
    return testClass;
  }

  final @NotNull TestClass getNotInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertFalse(testClass.isInstrumented);
    return testClass;
  }

  @NotNull
  final TestClass prepareTest(@SuppressWarnings("SameParameterValue") boolean printClassFiles) throws IOException {
    List<File> classFiles = compileTestFiles();
    MyClassLoader classLoader = new MyClassLoader(getClass().getClassLoader());
    TestClass testClass = null;
    classFiles.sort(Comparator.comparing(File::getName));
    for (File classFile : classFiles) {
      String className = FileUtil.getNameWithoutExtension(classFile.getName());
      byte[] classData = FileUtil.loadFileBytes(classFile);
      if (!className.equals(getTestName(false))) {
        classLoader.doDefineClass(null, classData);
      }
      else {
        byte[] instrumentedClassData = TMHTestUtil.instrument(classData, useThreadingAssertions);
        if (instrumentedClassData != null) {
          testClass = new TestClass(classLoader.doDefineClass(null, instrumentedClassData), instrumentedClassData, true);
          if (printClassFiles) {
            TMHTestUtil.printDebugInfo(classData, instrumentedClassData);
          }
        }
        else {
          testClass = new TestClass(classLoader.doDefineClass(null, classData), classData, false);
        }
      }
    }
    assertNotNull("Class " + getTestName(false) + " not found!", testClass);
    return testClass;
  }

  @NotNull
  private List<File> compileTestFiles() throws IOException {
    File testFile = PathManagerEx.findFileUnderProjectHome(TEST_DATA_PATH + getTestName(false) + ".java", getClass());
    File classesDir = FileUtil.createTempDirectory("tmh-test-output", null);

    File dependenciesDir = PathManagerEx.findFileUnderProjectHome(TEST_DATA_PATH + dependencyPath, getClass());
    File[] dependencies = dependenciesDir.listFiles();
    if (dependencies == null || dependencies.length == 0) {
      throw new IllegalStateException("Cannot find dependencies at " + dependenciesDir.getAbsolutePath());
    }
    List<String> dependencyPaths = ContainerUtil.map(dependencies, File::getAbsolutePath);
    IdeaTestUtil.compileFile(testFile, classesDir, ArrayUtil.toStringArray(dependencyPaths));
    try (Stream<Path> walk = Files.walk(Paths.get(classesDir.getAbsolutePath()))) {
      return walk
        .filter(Files::isRegularFile)
        .map(Path::toFile)
        .collect(Collectors.toList());
    }
  }

  static void invokeMethod(@NotNull Class<?> testClass) {
    rethrowExceptions(() -> {
      Object instance = testClass.getDeclaredConstructor().newInstance();
      Method method = testClass.getMethod("test");
      method.invoke(instance);
    });
  }

  private static void rethrowExceptions(@NotNull ThrowableRunnable<? extends Throwable> runnable) {
    try {
      runnable.run();
    }
    catch (Throwable e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof InvocationTargetException) {
        ExceptionUtil.rethrowAllAsUnchecked(e.getCause());
      }
      ExceptionUtil.rethrowAllAsUnchecked(e);
    }
  }

  static void executeInBackground(@NotNull ThrowableRunnable<? extends Throwable> runnable) throws ExecutionException {
    waitResult(startInBackground(runnable));
  }

  private static @NotNull Future<?> startInBackground(@NotNull ThrowableRunnable<? extends Throwable> runnable) {
    return Executors.newSingleThreadExecutor(r -> new Thread(r, TESTING_BACKGROUND_THREAD_NAME))
      .submit(() -> rethrowExceptions(runnable));
  }

  private static void waitResult(@NotNull Future<?> future) throws ExecutionException {
    try {
      future.get(10, TimeUnit.MINUTES);
    }
    catch (InterruptedException | TimeoutException e) {
      e.printStackTrace();
      fail("Background computation didn't finish as expected");
    }
  }

  private static class MyClassLoader extends ClassLoader {
    MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class<?> doDefineClass(String name, byte[] data) {
      return defineClass(name, data, 0, data.length);
    }
  }

  static class TestClass {
    final Class<?> aClass;
    final byte[] classBytes;
    final boolean isInstrumented;

    TestClass(Class<?> aClass, byte[] classBytes, boolean isInstrumented) {
      this.aClass = aClass;
      this.classBytes = classBytes;
      this.isInstrumented = isInstrumented;
    }
  }
}
