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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TMHInstrumenterTest extends UsefulTestCase {
  private static final String TEST_DATA_PATH = "plugins/devkit/jps-plugin/testData/threadingModelHelper/assertEdtInstrumenter/";

  private static final String ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/fake/RequiresEdt";
  private static final String APPLICATION_MANAGER_CLASS_NAME = "com/intellij/openapi/application/fake/ApplicationManager";
  private static final String APPLICATION_CLASS_NAME = "com/intellij/openapi/application/fake/Application";

  private static final String TESTING_BACKGROUND_THREAD_NAME = "TESTING_BACKGROUND_THREAD";
  private static final String CALLED_OUTSIDE_AWT_MESSAGE = "Access is allowed from event dispatch thread only.";

  public void testSimple() throws Exception {
    doTest();
  }

  public void testSecondMethod() throws Exception {
    doTest();
  }

  public void testMethodHasOtherAnnotationBefore() throws Exception {
    doTest();
  }

  public void testMethodHasOtherAnnotationAfter() throws Exception {
    doTest();
  }

  public void testEmptyBody() throws Exception {
    doTest();
  }

  public void testConstructor() throws Exception {
    Class<?> testClass = getInstrumentedTestClass();
    testClass.getDeclaredConstructor().newInstance();
    assertThrows(Throwable.class, CALLED_OUTSIDE_AWT_MESSAGE,
                 () -> executeInBackground(() -> testClass.getDeclaredConstructor().newInstance()));
  }

  public void testDoNotInstrument() throws Exception {
    Class<?> testClass = getNotInstrumentedTestClass();
    invokeMethod(testClass);
    executeInBackground(() -> invokeMethod(testClass));
  }

  private void doTest() throws Exception {
    Class<?> testClass = getInstrumentedTestClass();
    invokeMethod(testClass);
    assertThrows(Throwable.class, CALLED_OUTSIDE_AWT_MESSAGE, () -> executeInBackground(() -> invokeMethod(testClass)));
  }

  private @NotNull Class<?> getInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertTrue(testClass.isInstrumented);
    return testClass.aClass;
  }

  private @NotNull Class<?> getNotInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertFalse(testClass.isInstrumented);
    return testClass.aClass;
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
          testClass = new TestClass(classLoader.doDefineClass(null, instrumentedClassData), true);
          if (printClassFiles) {
            TMHTestUtil.printDebugInfo(classData, instrumentedClassData);
          }
        }
        else {
          testClass = new TestClass(classLoader.doDefineClass(null, classData), false);
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
    boolean instrumented = TMHInstrumenter.instrument(reader, writer, Collections.singleton(
      new TMHAssertionGenerator.AssertEdt(ANNOTATION_CLASS_NAME, APPLICATION_MANAGER_CLASS_NAME, APPLICATION_CLASS_NAME))
    );
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
    final boolean isInstrumented;

    TestClass(Class<?> aClass, boolean isInstrumented) {
      this.aClass = aClass;
      this.isInstrumented = isInstrumented;
    }
  }
}