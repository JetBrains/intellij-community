/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import junit.framework.*;
import org.intellij.lang.annotations.RegExp;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class UsefulTestCase extends TestCase {
  public static final String IDEA_MARKER_CLASS = "com.intellij.openapi.components.impl.stores.IdeaProjectStoreImpl";
  public static final String TEMP_DIR_MARKER = "unitTest_";

  protected static boolean OVERWRITE_TESTDATA = false;

  private static final String DEFAULT_SETTINGS_EXTERNALIZED;
  private static final Random RNG = new SecureRandom();
  private static final String ORIGINAL_TEMP_DIR = FileUtil.getTempDirectory();

  protected final Disposable myTestRootDisposable = new Disposable() {
    @Override
    public void dispose() { }

    @Override
    public String toString() {
      String testName = getTestName(false);
      return UsefulTestCase.this.getClass() + (StringUtil.isEmpty(testName) ? "" : ".test" + testName);
    }
  };

  protected static String ourPathToKeep = null;

  private CodeStyleSettings myOldCodeStyleSettings;
  private String myTempDir;

  protected static final Key<String> CREATION_PLACE = Key.create("CREATION_PLACE");

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");

    try {
      CodeInsightSettings defaultSettings = new CodeInsightSettings();
      Element oldS = new Element("temp");
      defaultSettings.writeExternal(oldS);
      DEFAULT_SETTINGS_EXTERNALIZED = JDOMUtil.writeElement(oldS, "\n");
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

    PathManager.ensureConfigFolderExists();

    if (shouldContainTempFiles()) {
      String testName = getTestName(true);
      if (StringUtil.isEmptyOrSpaces(testName)) testName = "";
      testName = new File(testName).getName(); // in case the test name contains file separators
      myTempDir = FileUtil.toSystemDependentName(ORIGINAL_TEMP_DIR + "/" + TEMP_DIR_MARKER + testName + "_"+ RNG.nextInt(1000));
      FileUtil.resetCanonicalTempPathCache(myTempDir);
    }
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    DocumentImpl.CHECK_DOCUMENT_CONSISTENCY = !isPerformanceTest();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myTestRootDisposable);
      cleanupSwingDataStructures();
      cleanupDeleteOnExitHookList();
    }
    finally {
      if (shouldContainTempFiles()) {
        FileUtil.resetCanonicalTempPathCache(ORIGINAL_TEMP_DIR);
        if (ourPathToKeep != null && FileUtil.isAncestor(myTempDir, ourPathToKeep, false)) {
          File[] files = new File(myTempDir).listFiles();
          if (files != null) {
            for (File file : files) {
              if (!FileUtil.pathsEqual(file.getPath(), ourPathToKeep)) {
                FileUtil.delete(file);
              }
            }
          }
        }
        else {
          FileUtil.delete(new File(myTempDir));
        }
      }
    }

    UIUtil.removeLeakingAppleListeners();
    super.tearDown();
  }

  private static final Set<String> DELETE_ON_EXIT_HOOK_DOT_FILES;
  private static final Class DELETE_ON_EXIT_HOOK_CLASS;
  static {
    Class<?> aClass = null;
    Set<String> files = null;
    try {
      aClass = Class.forName("java.io.DeleteOnExitHook");
      files = ReflectionUtil.getField(aClass, null, Set.class, "files");
    }
    catch (Exception e) {
    }
    DELETE_ON_EXIT_HOOK_CLASS = aClass;
    DELETE_ON_EXIT_HOOK_DOT_FILES = files;
  }

  public static void cleanupDeleteOnExitHookList() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    // try to reduce file set retained by java.io.DeleteOnExitHook
    List<String> list;
    synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
      if (DELETE_ON_EXIT_HOOK_DOT_FILES.isEmpty()) return;
      list = new ArrayList<String>(DELETE_ON_EXIT_HOOK_DOT_FILES);
    }
    for (int i = list.size() - 1; i >= 0; i--) {
      String path = list.get(i);
      if (FileSystemUtil.getAttributes(path) == null || new File(path).delete()) {
        synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
          DELETE_ON_EXIT_HOOK_DOT_FILES.remove(path);
        }
      }
    }
  }

  private static void cleanupSwingDataStructures() throws Exception {
    Class<?> aClass = Class.forName("javax.swing.KeyboardManager");

    Method get = aClass.getMethod("getCurrentManager");
    get.setAccessible(true);
    Object manager = get.invoke(null);
    {
      Field mapF = aClass.getDeclaredField("componentKeyStrokeMap");
      mapF.setAccessible(true);
      Object map = mapF.get(manager);
      ((Map)map).clear();
    }
    {
      Field mapF = aClass.getDeclaredField("containerMap");
      mapF.setAccessible(true);
      Object map = mapF.get(manager);
      ((Map)map).clear();
    }

    //Constructor<?> ctr = aClass.getDeclaredConstructor();
    //ctr.setAccessible(true);
    //Object newManager = ctr.newInstance();
    //Method setter = aClass.getDeclaredMethod("setCurrentManager", aClass);
    //setter.setAccessible(true);
    //setter.invoke(null, newManager);
  }

  protected void checkForSettingsDamage() throws Exception {
    if (isPerformanceTest() || ApplicationManager.getApplication() == null || ApplicationManager.getApplication() instanceof MockApplication) {
      return;
    }
    CodeStyleSettings oldCodeStyleSettings = myOldCodeStyleSettings;
    myOldCodeStyleSettings = null;

    doCheckForSettingsDamage(oldCodeStyleSettings, getCurrentCodeStyleSettings());
  }

  public static void doCheckForSettingsDamage(@NotNull CodeStyleSettings oldCodeStyleSettings,
                                              @NotNull CodeStyleSettings currentCodeStyleSettings) throws Exception {
    CompositeException result = new CompositeException();
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    try {
      Element newS = new Element("temp");
      settings.writeExternal(newS);
      Assert.assertEquals("Code insight settings damaged", DEFAULT_SETTINGS_EXTERNALIZED, JDOMUtil.writeElement(newS, "\n"));
    }
    catch (AssertionError error) {
      CodeInsightSettings clean = new CodeInsightSettings();
      Element temp = new Element("temp");
      clean.writeExternal(temp);
      settings.loadState(temp);
      result.add(error);
    }

    currentCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
    try {
      checkSettingsEqual(oldCodeStyleSettings, currentCodeStyleSettings, "Code style settings damaged");
    }
    catch (AssertionError e) {
      result.add(e);
    }
    finally {
      currentCodeStyleSettings.clearCodeStyleSettings();
    }

    try {
      InplaceRefactoring.checkCleared();
    }
    catch (AssertionError e) {
      result.add(e);
    }
    try {
      StartMarkAction.checkCleared();
    }
    catch (AssertionError e) {
      result.add(e);
    }

    if (!result.isEmpty()) throw result;
  }

  protected void storeSettings() {
    if (!isPerformanceTest() && ApplicationManager.getApplication() != null) {
      myOldCodeStyleSettings = getCurrentCodeStyleSettings().clone();
      myOldCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
    }
  }

  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyleSettingsManager.getInstance().getCurrentSettings();
  }

  public Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  @Override
  protected void runTest() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          UsefulTestCase.super.runTest();
        }
        catch (InvocationTargetException e) {
          e.fillInStackTrace();
          throwables[0] = e.getTargetException();
        }
        catch (IllegalAccessException e) {
          e.fillInStackTrace();
          throwables[0] = e;
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
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

  public static void edt(Runnable r) {
    UIUtil.invokeAndWaitIfNeeded(r);
  }

  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    UIUtil.invokeAndWaitIfNeeded(runnable);
    //runnable.run();
  }

  protected void defaultRunBare() throws Throwable {
    super.runBare();
  }

  public void runBare() throws Throwable {
    if (!shouldRunTest()) return;

    if (runInDispatchThread()) {
      final Throwable[] exception = {null};
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            defaultRunBare();
          }
          catch (Throwable tearingDown) {
            if (exception[0] == null) exception[0] = tearingDown;
          }
        }
      });
      if (exception[0] != null) throw exception[0];
    }
    else {
      defaultRunBare();
    }
  }

  protected boolean runInDispatchThread() {
    return true;
  }

  @NonNls
  public static String toString(Iterable<?> collection) {
    if (!collection.iterator().hasNext()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      if (o instanceof THashSet) {
        builder.append(new TreeSet<Object>((THashSet)o));
      }
      else {
        builder.append(o);
      }
      builder.append("\n");
    }
    return builder.toString();
  }

  public static <T> void assertOrderedEquals(T[] actual, T... expected) {
    assertOrderedEquals(Arrays.asList(actual), expected);
  }

  public static <T> void assertOrderedEquals(Iterable<T> actual, T... expected) {
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

  public static <T> void assertOrderedEquals(final String errorMsg, @NotNull Iterable<T> actual, @NotNull T... expected) {
    Assert.assertNotNull(actual);
    Assert.assertNotNull(expected);
    assertOrderedEquals(errorMsg, actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(final Iterable<? extends T> actual, final Collection<? extends T> expected) {
    assertOrderedEquals(null, actual, expected);
  }

  public static <T> void assertOrderedEquals(final String erroMsg,
                                             final Iterable<? extends T> actual,
                                             final Collection<? extends T> expected) {
    ArrayList<T> list = new ArrayList<T>();
    for (T t : actual) {
      list.add(t);
    }
    if (!list.equals(new ArrayList<T>(expected))) {
      String expectedString = toString(expected);
      String actualString = toString(actual);
      Assert.assertEquals(erroMsg, expectedString, actualString);
      Assert.fail("Warning! 'toString' do not reflect the difference.\nExpected: " + expectedString + "\nActual: " + actualString);
    }
  }

  public static <T> void assertOrderedCollection(T[] collection, @NotNull Consumer<T>... checkers) {
    Assert.assertNotNull(collection);
    assertOrderedCollection(Arrays.asList(collection), checkers);
  }

  public static <T> void assertSameElements(T[] collection, T... expected) {
    assertSameElements(Arrays.asList(collection), expected);
  }

  public static <T> void assertSameElements(Collection<? extends T> collection, T... expected) {
    assertSameElements(collection, Arrays.asList(expected));
  }

  public static <T> void assertSameElements(Collection<? extends T> collection, Collection<T> expected) {
    assertSameElements(null, collection, expected);
  }

  public static <T> void assertSameElements(String message, Collection<? extends T> collection, Collection<T> expected) {
    assertNotNull(collection);
    assertNotNull(expected);
    if (collection.size() != expected.size() || !new HashSet<T>(expected).equals(new HashSet<T>(collection))) {
      Assert.assertEquals(message, toString(expected, "\n"), toString(collection, "\n"));
      Assert.assertEquals(message, new HashSet<T>(expected), new HashSet<T>(collection));
    }
  }

  public <T> void assertContainsOrdered(Collection<? extends T> collection, T... expected) {
    assertContainsOrdered(collection, Arrays.asList(expected));
  }

  public <T> void assertContainsOrdered(Collection<? extends T> collection, Collection<T> expected) {
    ArrayList<T> copy = new ArrayList<T>(collection);
    copy.retainAll(expected);
    assertOrderedEquals(toString(collection), copy, expected);
  }

  public <T> void assertContainsElements(Collection<? extends T> collection, T... expected) {
    assertContainsElements(collection, Arrays.asList(expected));
  }

  public <T> void assertContainsElements(Collection<? extends T> collection, Collection<T> expected) {
    ArrayList<T> copy = new ArrayList<T>(collection);
    copy.retainAll(expected);
    assertSameElements(toString(collection), copy, expected);
  }

  public static String toString(Object[] collection, String separator) {
    return toString(Arrays.asList(collection), separator);
  }

  public <T> void assertDoesntContain(Collection<? extends T> collection, T... notExpected) {
    assertDoesntContain(collection, Arrays.asList(notExpected));
  }

  public <T> void assertDoesntContain(Collection<? extends T> collection, Collection<T> notExpected) {
    ArrayList<T> expected = new ArrayList<T>(collection);
    expected.removeAll(notExpected);
    assertSameElements(collection, expected);
  }

  public static String toString(Collection<?> collection, String separator) {
    List<String> list = ContainerUtil.map2List(collection, new Function<Object, String>() {
      @Override
      public String fun(final Object o) {
        return String.valueOf(o);
      }
    });
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

  public static <T> void assertOrderedCollection(Collection<? extends T> collection, Consumer<T>... checkers) {
    Assert.assertNotNull(collection);
    if (collection.size() != checkers.length) {
      Assert.fail(toString(collection));
    }
    int i = 0;
    for (final T actual : collection) {
      try {
        checkers[i].consume(actual);
      }
      catch (AssertionFailedError e) {
        System.out.println(i + ": " + actual);
        throw e;
      }
      i++;
    }
  }

  public static <T> void assertUnorderedCollection(T[] collection, Consumer<T>... checkers) {
    assertUnorderedCollection(Arrays.asList(collection), checkers);
  }

  public static <T> void assertUnorderedCollection(Collection<? extends T> collection, Consumer<T>... checkers) {
    Assert.assertNotNull(collection);
    if (collection.size() != checkers.length) {
      Assert.fail(toString(collection));
    }
    Set<Consumer<T>> checkerSet = new HashSet<Consumer<T>>(Arrays.asList(checkers));
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
        lastError.printStackTrace();
        Assert.fail("Incorrect element(" + i + "): " + actual);
      }
      i++;
    }
  }

  private static <T> Throwable accepts(final Consumer<T> condition, final T actual) {
    try {
      condition.consume(actual);
      return null;
    }
    catch (Throwable e) {
      return e;
    }
  }

  public static <T> T assertInstanceOf(Object o, Class<T> aClass) {
    Assert.assertNotNull("Expected instance of: " + aClass.getName() + " actual: " + null, o);
    Assert.assertTrue("Expected instance of: " + aClass.getName() + " actual: " + o.getClass().getName(), aClass.isInstance(o));
    return (T)o;
  }

  public static <T> T assertOneElement(Collection<T> collection) {
    Assert.assertNotNull(collection);
    Assert.assertEquals(toString(collection), 1, collection.size());
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T[] ts) {
    Assert.assertNotNull(ts);
    Assert.assertEquals(Arrays.asList(ts).toString(), 1, ts.length);
    return ts[0];
  }

  public static <T> void assertOneOf(T value, T... values) {
    boolean found = false;
    for (T v : values) {
      if (value == v || (value != null && value.equals(v))) {
        found = true;
      }
    }
    Assert.assertTrue("" + value + " should be equal to one of " + Arrays.toString(values), found);
  }

  public static void printThreadDump() {
    PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
  }

  public static void assertEmpty(final Object[] array) {
    assertOrderedEquals(array);
  }

  public static void assertEmpty(final Collection<?> collection) {
    assertEmpty(collection.toString(), collection);
  }
  public static void assertNullOrEmpty(final Collection<?> collection) {
    if (collection == null) return;
    assertEmpty(null, collection);
  }

  public static void assertEmpty(final String s) {
    assertTrue(s, StringUtil.isEmpty(s));
  }

  public static void assertEmpty(final String errorMsg, final Collection<?> collection) {
    Iterable<Object> i = (Iterable<Object>)collection;
    assertOrderedEquals(errorMsg, i);
  }

  public static void assertSize(int expectedSize, final Object[] array) {
    assertEquals(toString(Arrays.asList(array)), expectedSize, array.length);
  }

  public static void assertSize(int expectedSize, final Collection<?> c) {
    assertEquals(toString(c), expectedSize, c.size());
  }

  protected <T extends Disposable> T disposeOnTearDown(final T disposable) {
    Disposer.register(myTestRootDisposable, disposable);
    return disposable;
  }

  public static void assertSameLines(String expected, String actual) {
    String expectedText = StringUtil.convertLineSeparators(expected.trim());
    String actualText = StringUtil.convertLineSeparators(actual.trim());
    Assert.assertEquals(expectedText, actualText);
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    return getTestName(name, lowercaseFirstLetter);
  }

  public static String getTestName(String name, boolean lowercaseFirstLetter) {
    if (name == null) {
      return "";
    }
    name = StringUtil.trimStart(name, "test");
    if (StringUtil.isEmpty(name)) {
      return "";
    }
    return lowercaseFirstLetter(name, lowercaseFirstLetter);
  }

  public static String lowercaseFirstLetter(String name, boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  public static boolean isAllUppercaseName(String name) {
    int uppercaseChars = 0;
    for (int i = 0; i < name.length(); i++) {
      if (Character.isLowerCase(name.charAt(i))) {
        return false;
      }
      if (Character.isUpperCase(name.charAt(i))) {
        uppercaseChars++;
      }
    }
    return uppercaseChars >= 3;
  }

  protected String getTestDirectoryName() {
    final String testName = getTestName(true);
    return testName.replaceAll("_.*", "");
  }

  protected static void assertSameLinesWithFile(String filePath, String actualText) {
    String fileText;
    try {
      if (OVERWRITE_TESTDATA) {
        FileUtil.writeToFile(new File(filePath), actualText);
        System.out.println("File " + filePath + " created.");
      }
      fileText = FileUtil.loadFile(new File(filePath));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String expected = StringUtil.convertLineSeparators(fileText.trim());
    String actual = StringUtil.convertLineSeparators(actualText.trim());
    if (!Comparing.equal(expected, actual)) {
      throw new FileComparisonFailure(null, expected, actual, filePath);
    }
  }

  public static void clearFields(final Object test) throws IllegalAccessException {
    Class aClass = test.getClass();
    while (aClass != null) {
      clearDeclaredFields(test, aClass);
      aClass = aClass.getSuperclass();
    }
  }

  public static void clearDeclaredFields(Object test, Class aClass) throws IllegalAccessException {
    if (aClass == null) return;
    for (final Field field : aClass.getDeclaredFields()) {
      @NonNls final String name = field.getDeclaringClass().getName();
      if (!name.startsWith("junit.framework.") && !name.startsWith("com.intellij.testFramework.")) {
        final int modifiers = field.getModifiers();
        if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType().isPrimitive()) {
          field.setAccessible(true);
          field.set(test, null);
        }
      }
    }
  }

  protected static void checkSettingsEqual(JDOMExternalizable expected, JDOMExternalizable settings, String message) throws Exception {
    if (expected == null) {
      return;
    }
    if (settings == null) {
      return;
    }
    Element oldS = new Element("temp");
    expected.writeExternal(oldS);
    Element newS = new Element("temp");
    settings.writeExternal(newS);

    String newString = JDOMUtil.writeElement(newS, "\n");
    String oldString = JDOMUtil.writeElement(oldS, "\n");
    Assert.assertEquals(message, oldString, newString);
  }

  public boolean isPerformanceTest() {
    String name = getName();
    return name != null && name.contains("Performance") || getClass().getName().contains("Performance");
  }

  public static void doPostponedFormatting(final Project project) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
          }
        });
      }
    });
  }

  protected static void checkAllTimersAreDisposed() {
    try {
      Class<?> aClass = Class.forName("javax.swing.TimerQueue");

      Method inst = aClass.getDeclaredMethod("sharedInstance");
      inst.setAccessible(true);
      Object queue = inst.invoke(null);
      Field field = aClass.getDeclaredField("firstTimer");
      field.setAccessible(true);
      Object firstTimer = field.get(queue);
      if (firstTimer != null) {
        try {
          fail("Not disposed Timer: " + firstTimer.toString() + "; queue:" + queue);
        }
        finally {
          field.set(queue, null);
        }
      }
    }
    catch (Throwable e) {
      // Ignore
    }
  }

  /**
   * Checks that code block throw corresponding exception.
   *
   * @param exceptionCase Block annotated with some exception type
   * @throws Throwable
   */
  protected void assertException(final AbstractExceptionCase exceptionCase) throws Throwable {
    assertException(exceptionCase, null);
  }

  /**
   * Checks that code block throw corresponding exception with expected error msg.
   * If expected error message is null it will not be checked.
   *
   * @param exceptionCase    Block annotated with some exception type
   * @param expectedErrorMsg expected error messge
   * @throws Throwable
   */
  protected void assertException(final AbstractExceptionCase exceptionCase,
                                 @Nullable final String expectedErrorMsg) throws Throwable {
    assertExceptionOccurred(true, exceptionCase, expectedErrorMsg);
  }

  /**
   * Checks that code block doesn't throw corresponding exception.
   *
   * @param exceptionCase Block annotated with some exception type
   * @throws Throwable
   */
  protected void assertNoException(final AbstractExceptionCase exceptionCase) throws Throwable {
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

  private static void assertExceptionOccurred(boolean shouldOccur,
                                              AbstractExceptionCase exceptionCase,
                                              String expectedErrorMsg) throws Throwable {
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

        System.out.println("");
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

  protected boolean annotatedWith(@NotNull Class annotationClass) {
    Class aClass = getClass();
    String methodName = "test" + getTestName(false);
    boolean methodChecked = false;
    while (aClass != null && aClass != Object.class) {
      if (aClass.getAnnotation(annotationClass) != null) return true;
      if (!methodChecked) {
        try {
          Method method = aClass.getDeclaredMethod(methodName);
          if (method.getAnnotation(annotationClass) != null) return true;
          methodChecked = true;
        }
        catch (NoSuchMethodException e) {
        }
      }
      aClass = aClass.getSuperclass();
    }
    return false;
  }

  protected String getHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  protected static boolean isInHeadlessEnvironment() {
    return GraphicsEnvironment.isHeadless();
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

  public static @NotNull Test filteredSuite(@RegExp String regexp, @NotNull Test test) {
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
}
