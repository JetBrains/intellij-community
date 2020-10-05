// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
    doTest(TMHInstrumenterTest::invokeConstructor);
  }

  public void testDoNotInstrument() throws Exception {
    TestClass testClass = prepareTest(false);
    assertFalse(testClass.isInstrumented);
    assertNull(invokeMethod(testClass.aClass));
    assertNull(invokeInBackground(testClass.aClass, TMHInstrumenterTest::invokeMethod));
  }

  private void doTest() throws Exception {
    doTest(TMHInstrumenterTest::invokeMethod);
  }

  private void doTest(@NotNull ThrowableConvertor<Class<?>, Throwable, Exception> invoker) throws Exception {
    TestClass testClass = prepareTest(false);
    assertTrue(testClass.isInstrumented);
    assertNull(invoker.convert(testClass.aClass));
    Throwable throwable = invokeInBackground(testClass.aClass, invoker);
    assertNotNull(throwable);
    assertEquals(CALLED_OUTSIDE_AWT_MESSAGE, throwable.getMessage());
  }

  private static @Nullable Throwable invokeInBackground(@NotNull Class<?> testClass,
                                                        @NotNull ThrowableConvertor<Class<?>, Throwable, Exception> invoker) {
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, TESTING_BACKGROUND_THREAD_NAME));
    Future<Throwable> futureThrowable = executor.submit(() -> invoker.convert(testClass));
    try {
      return futureThrowable.get(10, TimeUnit.SECONDS);
    }
    catch (InterruptedException | TimeoutException e) {
      fail("Test failed to get result of the task from the ExecutorService: " + e.getCause().getMessage());
    }
    catch (ExecutionException e) {
      fail("Method call failed with unexpected exception when executed in background: " + e.getCause().getMessage());
    }
    return null;
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
            printDebugInfo(classData, instrumentedClassData);
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

  @Nullable
  private static Throwable invokeMethod(@NotNull Class<?> testClass) {
    try {
      Object instance = testClass.getDeclaredConstructor().newInstance();
      Method method = testClass.getMethod("test");
      method.invoke(instance);
    }
    catch (InvocationTargetException e) {
      return e.getCause();
    }
    catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
      fail("Failed to invoke test method: " + e.getMessage());
    }
    return null;
  }

  @Nullable
  private static Throwable invokeConstructor(@NotNull Class<?> testClass) {
    try {
      testClass.getDeclaredConstructor().newInstance();
    }
    catch (InvocationTargetException e) {
      return e.getCause();
    }
    catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
      fail("Failed to invoke constructor: " + e.getMessage());
    }
    return null;
  }

  private static void printDebugInfo(byte[] classData, byte[] instrumentedClassData) {
    printClass(classData);
    System.out.println();
    printClass(instrumentedClassData);
  }

  public static void printClass(byte[] data) {
    @SuppressWarnings("ImplicitDefaultCharsetUsage")
    PrintWriter printWriter = new PrintWriter(System.out);
    TraceClassVisitor visitor = new TraceClassVisitor(printWriter);
    new ClassReader(data).accept(visitor, 0);
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