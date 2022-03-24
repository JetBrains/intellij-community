// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassWriter;

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

public class TMHInstrumenterTest extends UsefulTestCase {
  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/instrumenter/";

  private static final String REQUIRES_EDT_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresEdt";
  private static final String REQUIRES_BACKGROUND_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresBackgroundThread";
  private static final String REQUIRES_READ_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresReadLock";
  private static final String REQUIRES_WRITE_LOCK_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresWriteLock";
  private static final String REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME =
    "com/intellij/util/concurrency/annotations/fake/RequiresReadLockAbsence";
  private static final String APPLICATION_MANAGER_CLASS_NAME = "com/intellij/openapi/application/fake/ApplicationManager";
  private static final String APPLICATION_CLASS_NAME = "com/intellij/openapi/application/fake/Application";

  private static final String TESTING_BACKGROUND_THREAD_NAME = "TESTING_BACKGROUND_THREAD";
  private static final String REQUIRES_EDT_MESSAGE = "Access is allowed from event dispatch thread only.";
  private static final String REQUIRES_BACKGROUND_THREAD_MESSAGE = "Access from event dispatch thread is not allowed.";

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
    assertThrows(Throwable.class, REQUIRES_EDT_MESSAGE,
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
    assertThrows(Throwable.class, REQUIRES_BACKGROUND_THREAD_MESSAGE, () -> invokeMethod(testClass.aClass));
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
    assertThrows(Throwable.class, REQUIRES_EDT_MESSAGE, () -> executeInBackground(() -> invokeMethod(testClass.aClass)));
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
        byte[] instrumentedClassData = instrument(classData);
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
    return Files.walk(Paths.get(classesDir.getAbsolutePath()))
      .filter(Files::isRegularFile)
      .map(Path::toFile)
      .collect(Collectors.toList());
  }

  private static byte @Nullable [] instrument(byte @NotNull [] classData) {
    FailSafeClassReader reader = new FailSafeClassReader(classData);
    int flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(reader));
    ClassWriter writer = new ClassWriter(reader, flags);
    boolean instrumented = TMHInstrumenter.instrument(reader, writer, ContainerUtil.set(
      new TMHAssertionGenerator.AssertEdt(REQUIRES_EDT_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertBackgroundThread(REQUIRES_BACKGROUND_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertReadAccess(REQUIRES_READ_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertWriteAccess(REQUIRES_WRITE_LOCK_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME),
      new TMHAssertionGenerator.AssertNoReadAccess(REQUIRES_READ_LOCK_ABSENCE_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME)
    ), true);
    return instrumented ? writer.toByteArray() : null;
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