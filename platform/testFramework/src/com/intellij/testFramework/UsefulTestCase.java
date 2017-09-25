/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.intellij.lang.annotations.RegExp;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class UsefulTestCase extends TestCase {
  public static final boolean IS_UNDER_TEAMCITY = System.getenv("TEAMCITY_VERSION") != null;
  public static final String TEMP_DIR_MARKER = "unitTest_";
  public static final boolean OVERWRITE_TESTDATA = Boolean.getBoolean("idea.tests.overwrite.data");

  private static final String DEFAULT_SETTINGS_EXTERNALIZED;
  private static final String ORIGINAL_TEMP_DIR = FileUtil.getTempDirectory();

  private static final Map<String, Long> TOTAL_SETUP_COST_MILLIS = new HashMap<>();
  private static final Map<String, Long> TOTAL_TEARDOWN_COST_MILLIS = new HashMap<>();

  static {
    Logger.setFactory(TestLoggerFactory.class);
  }
  protected static final Logger LOG = Logger.getInstance(UsefulTestCase.class);

  @NotNull
  private final Disposable myTestRootDisposable = new TestDisposable();

  static String ourPathToKeep;
  private final List<String> myPathsToKeep = new ArrayList<>();

  private CodeStyleSettings myOldCodeStyleSettings;
  private String myTempDir;

  static final Key<String> CREATION_PLACE = Key.create("CREATION_PLACE");

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");

    try {
      CodeInsightSettings defaultSettings = new CodeInsightSettings();
      Element oldS = new Element("temp");
      defaultSettings.writeExternal(oldS);
      DEFAULT_SETTINGS_EXTERNALIZED = JDOMUtil.writeElement(oldS);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected boolean shouldContainTempFiles() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    if (shouldContainTempFiles()) {
      String testName =  FileUtil.sanitizeFileName(getTestName(true));
      if (StringUtil.isEmptyOrSpaces(testName)) testName = "";
      testName = new File(testName).getName(); // in case the test name contains file separators
      myTempDir = new File(ORIGINAL_TEMP_DIR, TEMP_DIR_MARKER + testName).getPath();
      FileUtil.resetCanonicalTempPathCache(myTempDir);
    }
    boolean isStressTest = isStressTest();
    ApplicationInfoImpl.setInStressTest(isStressTest);
    if (isPerformanceTest()) {
      Timings.getStatistics();
    }
    // turn off Disposer debugging for performance tests
    Disposer.setDebugMode(!isStressTest);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      new RunAll(
        () -> disposeRootDisposable(),
        () -> cleanupSwingDataStructures(),
        () -> cleanupDeleteOnExitHookList(),
        () -> Disposer.setDebugMode(true),
        () -> {
          if (shouldContainTempFiles()) {
            FileUtil.resetCanonicalTempPathCache(ORIGINAL_TEMP_DIR);
            if (hasTmpFilesToKeep()) {
              File[] files = new File(myTempDir).listFiles();
              if (files != null) {
                for (File file : files) {
                  if (!shouldKeepTmpFile(file)) {
                    FileUtil.delete(file);
                  }
                }
              }
            }
            else {
              FileUtil.delete(new File(myTempDir));
            }
          }
        },
        () -> UIUtil.removeLeakingAppleListeners()
      ).run();
    }
    finally {
      super.tearDown();
    }
  }

  protected final void disposeRootDisposable() {
    Disposer.dispose(getTestRootDisposable());
  }

  protected void addTmpFileToKeep(@NotNull File file) {
    myPathsToKeep.add(file.getPath());
  }

  private boolean hasTmpFilesToKeep() {
    return ourPathToKeep != null && FileUtil.isAncestor(myTempDir, ourPathToKeep, false) || !myPathsToKeep.isEmpty();
  }

  private boolean shouldKeepTmpFile(@NotNull File file) {
    String path = file.getPath();
    if (FileUtil.pathsEqual(path, ourPathToKeep)) return true;
    for (String pathToKeep : myPathsToKeep) {
      if (FileUtil.pathsEqual(path, pathToKeep)) return true;
    }
    return false;
  }

  private static final Set<String> DELETE_ON_EXIT_HOOK_DOT_FILES;
  private static final Class DELETE_ON_EXIT_HOOK_CLASS;
  static {
    Class<?> aClass;
    try {
      aClass = Class.forName("java.io.DeleteOnExitHook");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    @SuppressWarnings("unchecked") Set<String> files = ReflectionUtil.getStaticFieldValue(aClass, Set.class, "files");
    DELETE_ON_EXIT_HOOK_CLASS = aClass;
    DELETE_ON_EXIT_HOOK_DOT_FILES = files;
  }

  @SuppressWarnings("SynchronizeOnThis")
  private static void cleanupDeleteOnExitHookList() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    // try to reduce file set retained by java.io.DeleteOnExitHook
    List<String> list;
    synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
      if (DELETE_ON_EXIT_HOOK_DOT_FILES.isEmpty()) return;
      list = new ArrayList<>(DELETE_ON_EXIT_HOOK_DOT_FILES);
    }
    for (int i = list.size() - 1; i >= 0; i--) {
      String path = list.get(i);
      File file = new File(path);
      if (file.delete() || !file.exists()) {
        synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
          DELETE_ON_EXIT_HOOK_DOT_FILES.remove(path);
        }
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  private static void cleanupSwingDataStructures() throws Exception {
    Object manager = ReflectionUtil.getDeclaredMethod(Class.forName("javax.swing.KeyboardManager"), "getCurrentManager").invoke(null);
    Map componentKeyStrokeMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "componentKeyStrokeMap");
    componentKeyStrokeMap.clear();
    Map containerMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "containerMap");
    containerMap.clear();
  }

  public static void checkForJdkTableLeaks(@NotNull Sdk[] oldSdks) {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    if (table != null) {
      Sdk[] jdks = table.getAllJdks();
      if (jdks.length != 0) {
        Set<Sdk> leaked = new THashSet<>(Arrays.asList(jdks));
        Set<Sdk> old = new THashSet<>(Arrays.asList(oldSdks));
        leaked.removeAll(old);

        try {
          if (!leaked.isEmpty()) {
            fail("Leaked SDKs: " + leaked);
          }
        }
        finally {
          for (Sdk jdk : leaked) {
            WriteAction.run(()-> table.removeJdk(jdk));
          }
        }
      }
    }
  }

  protected void checkForSettingsDamage() {
    Application app = ApplicationManager.getApplication();
    if (isStressTest() || app == null || app instanceof MockApplication) {
      return;
    }

    CodeStyleSettings oldCodeStyleSettings = myOldCodeStyleSettings;
    if (oldCodeStyleSettings == null) {
      return;
    }

    myOldCodeStyleSettings = null;

    doCheckForSettingsDamage(oldCodeStyleSettings, getCurrentCodeStyleSettings());
  }

  public static void doCheckForSettingsDamage(@NotNull CodeStyleSettings oldCodeStyleSettings,
                                              @NotNull CodeStyleSettings currentCodeStyleSettings) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    new RunAll()
      .append(() -> {
        try {
          Element newS = new Element("temp");
          settings.writeExternal(newS);
          Assert.assertEquals("Code insight settings damaged", DEFAULT_SETTINGS_EXTERNALIZED, JDOMUtil.writeElement(newS));
        }
        catch (AssertionError error) {
          CodeInsightSettings clean = new CodeInsightSettings();
          for (Field field : clean.getClass().getFields()) {
            try {
              ReflectionUtil.copyFieldValue(clean, settings, field);
            }
            catch (Exception ignored) {
            }
          }
          throw error;
        }
      })
      .append(() -> {
        currentCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
        try {
          checkSettingsEqual(oldCodeStyleSettings, currentCodeStyleSettings);
        }
        finally {
          currentCodeStyleSettings.clearCodeStyleSettings();
        }
      })
      .append(() -> InplaceRefactoring.checkCleared())
      .append(() -> StartMarkAction.checkCleared())
      .run();
  }

  void storeSettings() {
    if (!isStressTest() && ApplicationManager.getApplication() != null) {
      myOldCodeStyleSettings = getCurrentCodeStyleSettings().clone();
      myOldCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
    }
  }

  @NotNull
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getInstance().getCurrentSettings();
  }

  @NotNull
  public Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  @Override
  protected void runTest() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = () -> {
      try {
        super.runTest();
        TestLoggerFactory.onTestFinished(true);
      }
      catch (InvocationTargetException e) {
        TestLoggerFactory.onTestFinished(false);
        e.fillInStackTrace();
        throwables[0] = e.getTargetException();
      }
      catch (IllegalAccessException e) {
        TestLoggerFactory.onTestFinished(false);
        e.fillInStackTrace();
        throwables[0] = e;
      }
      catch (Throwable e) {
        TestLoggerFactory.onTestFinished(false);
        throwables[0] = e;
      }
    };

    invokeTestRunnable(runnable);

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  protected boolean shouldRunTest() {
    return PlatformTestUtil.canRunTest(getClass());
  }

  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    EdtTestUtilKt.runInEdtAndWait(() -> {
      runnable.run();
      return null;
    });
  }

  protected void defaultRunBare() throws Throwable {
    Throwable exception = null;
    try {
      long setupStart = System.nanoTime();
      setUp();
      long setupCost = (System.nanoTime() - setupStart) / 1000000;
      logPerClassCost(setupCost, TOTAL_SETUP_COST_MILLIS);

      runTest();
    }
    catch (Throwable running) {
      exception = running;
    }
    finally {
      try {
        long teardownStart = System.nanoTime();
        tearDown();
        long teardownCost = (System.nanoTime() - teardownStart) / 1000000;
        logPerClassCost(teardownCost, TOTAL_TEARDOWN_COST_MILLIS);
      }
      catch (Throwable tearingDown) {
        if (exception == null) exception = tearingDown;
      }
    }
    if (exception != null) throw exception;
  }

  /**
   * Logs the setup cost grouped by test fixture class (superclass of the current test class).
   *
   * @param cost setup cost in milliseconds
   */
  private void logPerClassCost(long cost, @NotNull Map<String, Long> costMap) {
    Class<?> superclass = getClass().getSuperclass();
    Long oldCost = costMap.get(superclass.getName());
    long newCost = oldCost == null ? cost : oldCost + cost;
    costMap.put(superclass.getName(), newCost);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void logSetupTeardownCosts() {
    System.out.println("Setup costs");
    long totalSetup = 0;
    for (Map.Entry<String, Long> entry : TOTAL_SETUP_COST_MILLIS.entrySet()) {
      System.out.println(String.format("  %s: %d ms", entry.getKey(), entry.getValue()));
      totalSetup += entry.getValue();
    }
    System.out.println("Teardown costs");
    long totalTeardown = 0;
    for (Map.Entry<String, Long> entry : TOTAL_TEARDOWN_COST_MILLIS.entrySet()) {
      System.out.println(String.format("  %s: %d ms", entry.getKey(), entry.getValue()));
      totalTeardown += entry.getValue();
    }
    System.out.println(String.format("Total overhead: setup %d ms, teardown %d ms", totalSetup, totalTeardown));
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.totalSetupMs' value='%d']", totalSetup));
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.totalTeardownMs' value='%d']", totalTeardown));
  }

  @Override
  public void runBare() throws Throwable {
    if (!shouldRunTest()) return;

    if (runInDispatchThread()) {
      TestRunnerUtil.replaceIdeEventQueueSafely();
      EdtTestUtil.runInEdtAndWait(this::defaultRunBare);
    }
    else {
      defaultRunBare();
    }
  }

  protected boolean runInDispatchThread() {
    return true;
  }

  /**
   * If you want a more shorter name than runInEdtAndWait.
   */
  protected void edt(@NotNull ThrowableRunnable<Throwable> runnable) {
    EdtTestUtil.runInEdtAndWait(runnable);
  }

  public static String toString(@NotNull Iterable<?> collection) {
    if (!collection.iterator().hasNext()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      if (o instanceof THashSet) {
        builder.append(new TreeSet<>((THashSet<?>)o));
      }
      else {
        builder.append(o);
      }
      builder.append('\n');
    }
    return builder.toString();
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(@NotNull T[] actual, @NotNull T... expected) {
    assertOrderedEquals(Arrays.asList(actual), expected);
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(@NotNull Iterable<T> actual, @NotNull T... expected) {
    assertOrderedEquals(null, actual, expected);
  }

  public static void assertOrderedEquals(@NotNull byte[] actual, @NotNull byte[] expected) {
    assertEquals(actual.length, expected.length);
    for (int i = 0; i < actual.length; i++) {
      byte a = actual[i];
      byte e = expected[i];
      assertEquals("not equals at index: "+i, e, a);
    }
  }

  public static void assertOrderedEquals(@NotNull int[] actual, @NotNull int[] expected) {
    if (actual.length != expected.length) {
      fail("Expected size: "+expected.length+"; actual: "+actual.length+"\nexpected: "+Arrays.toString(expected)+"\nactual  : "+Arrays.toString(actual));
    }
    for (int i = 0; i < actual.length; i++) {
      int a = actual[i];
      int e = expected[i];
      assertEquals("not equals at index: "+i, e, a);
    }
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(String errorMsg, @NotNull Iterable<T> actual, @NotNull T... expected) {
    assertOrderedEquals(errorMsg, actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(@NotNull Iterable<? extends T> actual, @NotNull Collection<? extends T> expected) {
    assertOrderedEquals(null, actual, expected);
  }

  public static <T> void assertOrderedEquals(String errorMsg,
                                             @NotNull Iterable<? extends T> actual,
                                             @NotNull Collection<? extends T> expected) {
    List<T> list = new ArrayList<>();
    for (T t : actual) {
      list.add(t);
    }
    if (!list.equals(new ArrayList<T>(expected))) {
      String expectedString = toString(expected);
      String actualString = toString(actual);
      Assert.assertEquals(errorMsg, expectedString, actualString);
      Assert.fail("Warning! 'toString' does not reflect the difference.\nExpected: " + expectedString + "\nActual: " + actualString);
    }
  }

  @SafeVarargs
  public static <T> void assertOrderedCollection(@NotNull T[] collection, @NotNull Consumer<T>... checkers) {
    assertOrderedCollection(Arrays.asList(collection), checkers);
  }

  @SafeVarargs
  public static <T> void assertSameElements(@NotNull T[] collection, @NotNull T... expected) {
    assertSameElements(Arrays.asList(collection), expected);
  }

  @SafeVarargs
  public static <T> void assertSameElements(@NotNull Collection<? extends T> collection, @NotNull T... expected) {
    assertSameElements(collection, Arrays.asList(expected));
  }

  public static <T> void assertSameElements(@NotNull Collection<? extends T> collection, @NotNull Collection<T> expected) {
    assertSameElements(null, collection, expected);
  }

  public static <T> void assertSameElements(String message, @NotNull Collection<? extends T> collection, @NotNull Collection<T> expected) {
    if (collection.size() != expected.size() || !new HashSet<>(expected).equals(new HashSet<T>(collection))) {
      Assert.assertEquals(message, toString(expected, "\n"), toString(collection, "\n"));
      Assert.assertEquals(message, new HashSet<>(expected), new HashSet<T>(collection));
    }
  }

  @SafeVarargs
  public static <T> void assertContainsOrdered(@NotNull Collection<? extends T> collection, @NotNull T... expected) {
    assertContainsOrdered(collection, Arrays.asList(expected));
  }

  public static <T> void assertContainsOrdered(@NotNull Collection<? extends T> collection, @NotNull Collection<T> expected) {
    ArrayList<T> copy = new ArrayList<>(collection);
    copy.retainAll(expected);
    assertOrderedEquals(toString(collection), copy, expected);
  }

  @SafeVarargs
  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, @NotNull T... expected) {
    assertContainsElements(collection, Arrays.asList(expected));
  }

  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, @NotNull Collection<T> expected) {
    ArrayList<T> copy = new ArrayList<>(collection);
    copy.retainAll(expected);
    assertSameElements(toString(collection), copy, expected);
  }

  @NotNull
  public static String toString(@NotNull Object[] collection, @NotNull String separator) {
    return toString(Arrays.asList(collection), separator);
  }

  @SafeVarargs
  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, @NotNull T... notExpected) {
    assertDoesntContain(collection, Arrays.asList(notExpected));
  }

  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, @NotNull Collection<T> notExpected) {
    ArrayList<T> expected = new ArrayList<>(collection);
    expected.removeAll(notExpected);
    assertSameElements(collection, expected);
  }

  @NotNull
  public static String toString(@NotNull Collection<?> collection, @NotNull String separator) {
    List<String> list = ContainerUtil.map2List(collection, String::valueOf);
    Collections.sort(list);
    StringBuilder builder = new StringBuilder();
    boolean flag = false;
    for (final String o : list) {
      if (flag) {
        builder.append(separator);
      }
      builder.append(o);
      flag = true;
    }
    return builder.toString();
  }

  @SafeVarargs
  public static <T> void assertOrderedCollection(@NotNull Collection<? extends T> collection, @NotNull Consumer<T>... checkers) {
    if (collection.size() != checkers.length) {
      Assert.fail(toString(collection));
    }
    int i = 0;
    for (final T actual : collection) {
      try {
        checkers[i].consume(actual);
      }
      catch (AssertionFailedError e) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(i + ": " + actual);
        throw e;
      }
      i++;
    }
  }

  @SafeVarargs
  public static <T> void assertUnorderedCollection(@NotNull T[] collection, @NotNull Consumer<T>... checkers) {
    assertUnorderedCollection(Arrays.asList(collection), checkers);
  }

  @SafeVarargs
  public static <T> void assertUnorderedCollection(@NotNull Collection<? extends T> collection, @NotNull Consumer<T>... checkers) {
    if (collection.size() != checkers.length) {
      Assert.fail(toString(collection));
    }
    Set<Consumer<T>> checkerSet = new HashSet<>(Arrays.asList(checkers));
    int i = 0;
    Throwable lastError = null;
    for (final T actual : collection) {
      boolean flag = true;
      for (final Consumer<T> condition : checkerSet) {
        Throwable error = accepts(condition, actual);
        if (error == null) {
          checkerSet.remove(condition);
          flag = false;
          break;
        }
        else {
          lastError = error;
        }
      }
      if (flag) {
        //noinspection ConstantConditions,CallToPrintStackTrace
        lastError.printStackTrace();
        Assert.fail("Incorrect element(" + i + "): " + actual);
      }
      i++;
    }
  }

  private static <T> Throwable accepts(@NotNull Consumer<T> condition, final T actual) {
    try {
      condition.consume(actual);
      return null;
    }
    catch (Throwable e) {
      return e;
    }
  }

  @Contract("null, _ -> fail")
  public static <T> T assertInstanceOf(Object o, @NotNull Class<T> aClass) {
    Assert.assertNotNull("Expected instance of: " + aClass.getName() + " actual: " + null, o);
    Assert.assertTrue("Expected instance of: " + aClass.getName() + " actual: " + o.getClass().getName(), aClass.isInstance(o));
    @SuppressWarnings("unchecked") T t = (T)o;
    return t;
  }

  public static <T> T assertOneElement(@NotNull Collection<T> collection) {
    Iterator<T> iterator = collection.iterator();
    String toString = toString(collection);
    Assert.assertTrue(toString, iterator.hasNext());
    T t = iterator.next();
    Assert.assertFalse(toString, iterator.hasNext());
    return t;
  }

  public static <T> T assertOneElement(@NotNull T[] ts) {
    Assert.assertEquals(Arrays.asList(ts).toString(), 1, ts.length);
    return ts[0];
  }

  @SafeVarargs
  public static <T> void assertOneOf(T value, @NotNull T... values) {
    boolean found = false;
    for (T v : values) {
      if (value == v || value != null && value.equals(v)) {
        found = true;
      }
    }
    Assert.assertTrue(value + " should be equal to one of " + Arrays.toString(values), found);
  }

  public static void printThreadDump() {
    PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
  }

  public static void assertEmpty(@NotNull Object[] array) {
    assertOrderedEquals(array);
  }

  public static void assertNotEmpty(final Collection<?> collection) {
    if (collection == null) return;
    assertTrue(!collection.isEmpty());
  }

  public static void assertEmpty(@NotNull Collection<?> collection) {
    assertEmpty(collection.toString(), collection);
  }

  public static void assertNullOrEmpty(@Nullable Collection<?> collection) {
    if (collection == null) return;
    assertEmpty(null, collection);
  }

  public static void assertEmpty(final String s) {
    assertTrue(s, StringUtil.isEmpty(s));
  }

  public static <T> void assertEmpty(final String errorMsg, final Collection<T> collection) {
    assertOrderedEquals(errorMsg, collection, Collections.emptyList());
  }

  public static void assertSize(int expectedSize, final Object[] array) {
    assertEquals(toString(Arrays.asList(array)), expectedSize, array.length);
  }

  public static void assertSize(int expectedSize, final Collection<?> c) {
    assertEquals(toString(c), expectedSize, c.size());
  }

  protected <T extends Disposable> T disposeOnTearDown(final T disposable) {
    Disposer.register(getTestRootDisposable(), disposable);
    return disposable;
  }

  public static void assertSameLines(String expected, String actual) {
    String expectedText = StringUtil.convertLineSeparators(expected.trim());
    String actualText = StringUtil.convertLineSeparators(actual.trim());
    Assert.assertEquals(expectedText, actualText);
  }

  public static void assertExists(@NotNull File file){
    assertTrue("File should exist " + file, file.exists());
  }

  public static void assertDoesntExist(@NotNull File file){
    assertFalse("File should not exist " + file, file.exists());
  }

  @NotNull
  protected String getTestName(boolean lowercaseFirstLetter) {
    return getTestName(getName(), lowercaseFirstLetter);
  }

  @NotNull
  public static String getTestName(String name, boolean lowercaseFirstLetter) {
    return name == null ? "" : PlatformTestUtil.getTestName(name, lowercaseFirstLetter);
  }

  protected String getTestDirectoryName() {
    final String testName = getTestName(true);
    return testName.replaceAll("_.*", "");
  }

  public static void assertSameLinesWithFile(String filePath, String actualText) {
    assertSameLinesWithFile(filePath, actualText, true);
  }

  public static void assertSameLinesWithFile(String filePath, String actualText, boolean trimBeforeComparing) {
    String fileText;
    try {
      if (OVERWRITE_TESTDATA) {
        VfsTestUtil.overwriteTestData(filePath, actualText);
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("File " + filePath + " created.");
      }
      fileText = FileUtil.loadFile(new File(filePath), CharsetToolkit.UTF8_CHARSET);
    }
    catch (FileNotFoundException e) {
      VfsTestUtil.overwriteTestData(filePath, actualText);
      throw new AssertionFailedError("No output text found. File " + filePath + " created.");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String expected = StringUtil.convertLineSeparators(trimBeforeComparing ? fileText.trim() : fileText);
    String actual = StringUtil.convertLineSeparators(trimBeforeComparing ? actualText.trim() : actualText);
    if (!Comparing.equal(expected, actual)) {
      throw new FileComparisonFailure(null, expected, actual, filePath);
    }
  }

  protected static void clearFields(@NotNull Object test) throws IllegalAccessException {
    Class aClass = test.getClass();
    while (aClass != null) {
      clearDeclaredFields(test, aClass);
      aClass = aClass.getSuperclass();
    }
  }

  public static void clearDeclaredFields(Object test, Class aClass) throws IllegalAccessException {
    if (aClass == null) return;
    for (final Field field : aClass.getDeclaredFields()) {
      final String name = field.getDeclaringClass().getName();
      if (!name.startsWith("junit.framework.") && !name.startsWith("com.intellij.testFramework.")) {
        final int modifiers = field.getModifiers();
        if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType().isPrimitive()) {
          field.setAccessible(true);
          field.set(test, null);
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  private static void checkSettingsEqual(CodeStyleSettings expected, CodeStyleSettings settings) throws Exception {
    if (expected == null || settings == null) return;

    Element oldS = new Element("temp");
    expected.writeExternal(oldS);
    Element newS = new Element("temp");
    settings.writeExternal(newS);

    String newString = JDOMUtil.writeElement(newS);
    String oldString = JDOMUtil.writeElement(oldS);
    Assert.assertEquals("Code style settings damaged", oldString, newString);
  }

  public boolean isPerformanceTest() {
    String testName = getName();
    String className = getClass().getName();
    return isPerformanceTest(testName, className);
  }

  public static boolean isPerformanceTest(@Nullable String testName, @Nullable String className) {
    return testName != null && StringUtil.containsIgnoreCase(testName, "performance") ||
           className != null && StringUtil.containsIgnoreCase(className, "performance");
  }

  /**
   * @return true for a test which performs A LOT of computations.
   * Such test should typically avoid performing expensive checks, e.g. data structure consistency complex validations.
   * If you want your test to be treated as "Stress", please mention one of these words in its name: "Stress", "Slow".
   * For example: {@code public void testStressPSIFromDifferentThreads()}
   */
  public boolean isStressTest() {
    return isStressTest(getName(), getClass().getName());
  }

  private static boolean isStressTest(String testName, String className) {
    return isPerformanceTest(testName, className) ||
           containsStressWords(testName) ||
           containsStressWords(className);
  }

  private static boolean containsStressWords(@Nullable String name) {
    return name != null && (name.contains("Stress") || name.contains("Slow"));
  }

  public static void doPostponedFormatting(final Project project) {
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    });
  }

  /**
   * Checks that code block throw corresponding exception.
   *
   * @param exceptionCase Block annotated with some exception type
   */
  protected void assertException(final AbstractExceptionCase exceptionCase) throws Throwable {
    assertException(exceptionCase, null);
  }

  /**
   * Checks that code block throw corresponding exception with expected error msg.
   * If expected error message is null it will not be checked.
   *
   * @param exceptionCase    Block annotated with some exception type
   * @param expectedErrorMsg expected error message
   */
  protected void assertException(AbstractExceptionCase exceptionCase, @Nullable String expectedErrorMsg) throws Throwable {
    //noinspection unchecked
    assertExceptionOccurred(true, exceptionCase, expectedErrorMsg);
  }

  /**
   * Checks that the code block throws an exception of the specified class.
   *
   * @param exceptionClass   Expected exception type
   * @param runnable         Block annotated with some exception type
   */
  public static <T extends Throwable> void assertThrows(@NotNull Class<? extends Throwable> exceptionClass,
                                                           @NotNull ThrowableRunnable<T> runnable) throws T {
    assertThrows(exceptionClass, null, runnable);
  }

  /**
   * Checks that the code block throws an exception of the specified class with expected error msg.
   * If expected error message is null it will not be checked.
   *
   * @param exceptionClass   Expected exception type
   * @param expectedErrorMsg expected error message, of any
   * @param runnable         Block annotated with some exception type
   */
  @SuppressWarnings({"unchecked", "SameParameterValue"})
  public static <T extends Throwable> void assertThrows(@NotNull Class<? extends Throwable> exceptionClass,
                                                        @Nullable String expectedErrorMsg,
                                                        @NotNull ThrowableRunnable<T> runnable) throws T {
    assertExceptionOccurred(true, new AbstractExceptionCase() {
      @Override
      public Class<Throwable> getExpectedExceptionClass() {
        return (Class<Throwable>)exceptionClass;
      }

      @Override
      public void tryClosure() throws Throwable {
        runnable.run();
      }
    }, expectedErrorMsg);
  }

  /**
   * Checks that code block doesn't throw corresponding exception.
   *
   * @param exceptionCase Block annotated with some exception type
   */
  protected <T extends Throwable> void assertNoException(final AbstractExceptionCase<T> exceptionCase) throws T {
    assertExceptionOccurred(false, exceptionCase, null);
  }

  protected void assertNoThrowable(final Runnable closure) {
    String throwableName = null;
    try {
      closure.run();
    }
    catch (Throwable thr) {
      throwableName = thr.getClass().getName();
    }
    assertNull(throwableName);
  }

  private static <T extends Throwable> void assertExceptionOccurred(boolean shouldOccur,
                                                                    AbstractExceptionCase<T> exceptionCase,
                                                                    String expectedErrorMsg) throws T {
    boolean wasThrown = false;
    try {
      exceptionCase.tryClosure();
    }
    catch (Throwable e) {
      if (shouldOccur) {
        wasThrown = true;
        final String errorMessage = exceptionCase.getAssertionErrorMessage();
        assertEquals(errorMessage, exceptionCase.getExpectedExceptionClass(), e.getClass());
        if (expectedErrorMsg != null) {
          assertEquals("Compare error messages", expectedErrorMsg, e.getMessage());
        }
      }
      else if (exceptionCase.getExpectedExceptionClass().equals(e.getClass())) {
        wasThrown = true;

        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("");
        //noinspection UseOfSystemOutOrSystemErr
        e.printStackTrace(System.out);

        fail("Exception isn't expected here. Exception message: " + e.getMessage());
      }
      else {
        throw e;
      }
    }
    finally {
      if (shouldOccur && !wasThrown) {
        fail(exceptionCase.getAssertionErrorMessage());
      }
    }
  }

  protected boolean annotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
    Class<?> aClass = getClass();
    String methodName = "test" + getTestName(false);
    boolean methodChecked = false;
    while (aClass != null && aClass != Object.class) {
      if (aClass.getAnnotation(annotationClass) != null) return true;
      if (!methodChecked) {
        Method method = ReflectionUtil.getDeclaredMethod(aClass, methodName);
        if (method != null) {
          if (method.getAnnotation(annotationClass) != null) return true;
          methodChecked = true;
        }
      }
      aClass = aClass.getSuperclass();
    }
    return false;
  }

  protected String getHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  public static void refreshRecursively(@NotNull VirtualFile file) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        file.getChildren();
        return true;
      }
    });
    file.refresh(false, true);
  }

  @NotNull
  public static Test filteredSuite(@RegExp String regexp, @NotNull Test test) {
    final Pattern pattern = Pattern.compile(regexp);
    final TestSuite testSuite = new TestSuite();
    new Processor<Test>() {

      @Override
      public boolean process(Test test) {
        if (test instanceof TestSuite) {
          for (int i = 0, len = ((TestSuite)test).testCount(); i < len; i++) {
            process(((TestSuite)test).testAt(i));
          }
        }
        else if (pattern.matcher(test.toString()).find()) {
          testSuite.addTest(test);
        }
        return false;
      }
    }.process(test);
    return testSuite;
  }

  @Nullable
  public static VirtualFile refreshAndFindFile(@NotNull final File file) {
    return UIUtil.invokeAndWaitIfNeeded(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated
  public static final String IDEA_MARKER_CLASS = "com.intellij.openapi.roots.IdeaModifiableModelsProvider";
  //</editor-fold>

  protected class TestDisposable implements Disposable {
    private volatile boolean myDisposed;

    public TestDisposable() {
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }

    public boolean isDisposed() {
      return myDisposed;
    }

    @Override
    public String toString() {
      String testName = getTestName(false);
      return UsefulTestCase.this.getClass() + (StringUtil.isEmpty(testName) ? "" : ".test" + testName);
    }
  }
}