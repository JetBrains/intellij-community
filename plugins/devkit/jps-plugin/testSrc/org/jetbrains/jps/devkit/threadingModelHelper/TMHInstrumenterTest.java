// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_UNDER_EDT;
import static com.intellij.util.concurrency.ThreadingAssertions.MUST_NOT_EXECUTE_UNDER_EDT;

public class TMHInstrumenterTest extends UsefulTestCase {
  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/instrumenter/";

  private static final String TESTING_BACKGROUND_THREAD_NAME = "TESTING_BACKGROUND_THREAD";

  public void testSimple() throws Exception {
    doEdtTest();
  }

  public void testSecondMethod() throws Exception {
    doEdtTest();
  }

  public void testMethodHasOtherAnnotationBefore() throws Exception {
    doEdtTest();
  }

  public void testMethodHasOtherAnnotationAfter() throws Exception {
    doEdtTest();
  }

  public void testEmptyBody() throws Exception {
    doEdtTest();
  }

  public void testConstructor() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    testClass.aClass.getDeclaredConstructor().newInstance();
    assertThrows(Throwable.class, MUST_EXECUTE_UNDER_EDT,
                 () -> executeInBackground(() -> testClass.aClass.getDeclaredConstructor().newInstance()));
  }

  public void testDoNotInstrument() throws Exception {
    TestClass testClass = getNotInstrumentedTestClass();
    invokeMethod(testClass.aClass);
    executeInBackground(() -> invokeMethod(testClass.aClass));
  }

  public void testRequiresBackgroundThreadAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    executeInBackground(() -> invokeMethod(testClass.aClass));
    assertThrows(Throwable.class, MUST_NOT_EXECUTE_UNDER_EDT, () -> invokeMethod(testClass.aClass));
  }

  public void testRequiresReadLockAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertReadAccessAllowed"));
  }

  public void testRequiresWriteLockAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertWriteAccessAllowed"));
  }

  public void testRequiresReadLockAbsenceAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertReadAccessNotAllowed"));
  }

  public void testLineNumber() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 8, 8), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenBodyHasTwoStatements() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 8, 8, 9), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenEmptyBody() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 7, 7), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenOtherMethodBefore() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 7, 12, 12), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  private void doEdtTest() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    invokeMethod(testClass.aClass);
    assertThrows(Throwable.class, MUST_EXECUTE_UNDER_EDT, () -> executeInBackground(() -> invokeMethod(testClass.aClass)));
  }

  private @NotNull TestClass getInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertTrue(testClass.isInstrumented);
    return testClass;
  }

  private @NotNull TestClass getNotInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertFalse(testClass.isInstrumented);
    return testClass;
  }

  @NotNull
  protected TestClass prepareTest(@SuppressWarnings("SameParameterValue") boolean printClassFiles) throws IOException {
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
        byte[] instrumentedClassData = TMHTestUtil.instrument(classData);
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

    File dependenciesDir = PathManagerEx.findFileUnderProjectHome(TEST_DATA_PATH + "dependencies", getClass());
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

  private static void invokeMethod(@NotNull Class<?> testClass) {
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

  private static void executeInBackground(@NotNull ThrowableRunnable<? extends Throwable> runnable) throws ExecutionException {
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

  private static class TestClass {
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