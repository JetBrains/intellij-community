/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author peter
 */
public abstract class UsefulTestCase extends TestCase {
  protected final Disposable myTestRootDisposable = Disposer.newDisposable();
  private static final String DEFAULT_SETTINGS_EXTERNALIZED;
  private static CodeStyleSettings myOldCodeStyleSettings;

  protected static final Key<String> CREATION_PLACE = Key.create("CREATION_PLACE");

  static {
    System.setProperty("apple.awt.UIElement", "true"); // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.

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

  protected void tearDown() throws Exception {
    Disposer.dispose(myTestRootDisposable);
    cleanupSwingDataStructures();
    super.tearDown();
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
    if (isPerformanceTest() || ApplicationManager.getApplication() == null) {
      return;
    }
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
      throw error;
    }

    CodeStyleSettings codeStyleSettings = getCurrentCodeStyleSettings();
    codeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
    try {
      checkSettingsEqual(myOldCodeStyleSettings, codeStyleSettings, "Code style settings damaged");
    }
    finally {
      codeStyleSettings.clearCodeStyleSettings();
    }
    myOldCodeStyleSettings = null;

    VariableInplaceRenamer.checkCleared();
  }

  protected void storeSettings() {
    if (!isPerformanceTest() && ApplicationManager.getApplication() != null) {
      myOldCodeStyleSettings = getCurrentCodeStyleSettings().clone();
      myOldCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);
    }
  }

  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getInstance().getCurrentSettings();
  }

  protected Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  protected void runTest() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = new Runnable() {
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

  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    runnable.run();
  }

  @NonNls
  public static String toString(Collection<?> collection) {
    if (collection.isEmpty()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      if (o instanceof THashSet) {
        builder.append(new TreeSet<Object>((Collection<Object>)o));
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

  public static <T> void assertOrderedEquals(Collection<T> actual, T... expected) {
    assertOrderedEquals(null, actual, expected);
  }

  public static <T> void assertOrderedEquals(final String errorMsg, Collection<T> actual, T... expected) {
    Assert.assertNotNull(actual);
    Assert.assertNotNull(expected);
    assertOrderedEquals(errorMsg, actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(final Collection<? extends T> actual, final Collection<? extends T> expected) {
    assertOrderedEquals(null, actual, expected);
  }

  public static <T> void assertOrderedEquals(final String erroMsg, final Collection<? extends T> actual, final Collection<? extends T> expected) {
    if (!new ArrayList<T>(actual).equals(new ArrayList<T>(expected))) {
      Assert.assertEquals(erroMsg, toString(expected), toString(actual));
      Assert.fail();
    }
  }

  public static <T> void assertOrderedCollection(T[] collection, Consumer<T>... checkers) {
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
    if (collection.size() != expected.size() || !new HashSet<T>(expected).equals(new HashSet<T>(collection))) {
      Assert.assertEquals(toString(expected, "\n"), toString(collection, "\n"));
      Assert.assertEquals(new HashSet<T>(expected), new HashSet<T>(collection));
    }
  }

  public static String toString(Object[] collection, String separator) {
    return toString(Arrays.asList(collection), separator);
  }

  public static String toString(Collection<?> collection, String separator) {
    List<String> list = ContainerUtil.map2List(collection, new Function<Object,String>() {
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
    Assert.assertNotNull(o);
    Assert.assertTrue(o.getClass().getName(), aClass.isInstance(o));
    return (T)o;
  }

  public static <T> T assertOneElement(Collection<T> collection) {
    Assert.assertNotNull(collection);
    Assert.assertEquals(toString(collection), 1, collection.size());
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T[] ts) {
    Assert.assertNotNull(ts);
    Assert.assertEquals(1, ts.length);
    return ts[0];
  }

  public static void printThreadDump() {
    final Map<Thread,StackTraceElement[]> traces = Thread.getAllStackTraces();
    for (final Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
      System.out.println("\n" + entry.getKey().getName() + "\n");
      final StackTraceElement[] value = entry.getValue();
      for (final StackTraceElement stackTraceElement : value) {
        System.out.println(" at "+stackTraceElement);
      }
    }
  }

  public static void assertEmpty(final Object[] array) {
    assertOrderedEquals(array);
  }

  public static void assertEmpty(final Collection<?> collection) {
    assertEmpty(null, collection);
  }

  public static void assertEmpty(final String s) {
    assertTrue(s, StringUtil.isEmpty(s));
  }

  public static void assertEmpty(final String errorMsg, final Collection<?> collection) {
    assertOrderedEquals(errorMsg, collection);
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
    name = StringUtil.trimStart(name, "test");
    if (StringUtil.isEmpty(name)) {
      return "";
    }
    return getTestName(name, lowercaseFirstLetter);
  }

  public static String getTestName(String name, boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  public static boolean isAllUppercaseName(String name) {
    int uppercaseChars = 0;
    for(int i=0; i<name.length(); i++) {
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

  protected static void assertSameLinesWithFile(final String filePath, final String actualText) {
    String fileText;
    try {
      final FileReader reader = new FileReader(filePath);
      fileText = FileUtil.loadTextAndClose(reader);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertSameLines(fileText, actualText);
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
    try {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
            }
          });
        }
      });
    }
    catch (Throwable e) {
      // Way to go...
    }
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
          fail("Not disposed Timer: "+firstTimer.toString()+"; queue:"+queue);
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
   * @param exceptionCase Block annotated with some exception type
   * @throws Throwable
   */
  protected void assertException(final AbstractExceptionCase exceptionCase) throws Throwable {
    assertException(exceptionCase, null);
  }

  /**
   * Checks that code block throw corresponding exception with expected error msg.
   * If expected error message is null it will not be checked.
   * @param exceptionCase Block annotated with some exception type
   * @param expectedErrorMsg expected error messge
   * @throws Throwable
   */
  protected void assertException(final AbstractExceptionCase exceptionCase,
                                 @Nullable final String expectedErrorMsg) throws Throwable {
    assertExceptionOccurred(true, exceptionCase, expectedErrorMsg);
  }

  /**
   * Checks that code block doesn't throw corresponding exception.
   * @param exceptionCase Block annotated with some exception type
   * @throws Throwable
   */
  protected void assertNoException(final AbstractExceptionCase exceptionCase) throws Throwable {
    assertExceptionOccurred(false, exceptionCase, null);
  }

  protected void assertNoThrowable(final Runnable closure) {
    String throwableName = null;
    try{
      closure.run();
    } catch (Throwable thr) {
      throwableName = thr.getClass().getName();
    }
    assertNull(throwableName);
  }

  private void assertExceptionOccurred(boolean shouldOccur,
                                       AbstractExceptionCase exceptionCase,
                                       String expectedErrorMsg) throws Throwable {
    boolean wasThrown = false;
    try {
      exceptionCase.tryClosure();
    } catch (Throwable e) {
      if (shouldOccur) {
        wasThrown = true;
        final String errorMessage = exceptionCase.getAssertionErrorMessage();
        assertEquals(errorMessage, exceptionCase.getExpectedExceptionClass(), e.getClass());
        if (expectedErrorMsg != null) {
          assertEquals("Compare error messages", expectedErrorMsg, e.getMessage());
        }
      } else if (exceptionCase.getExpectedExceptionClass().equals(e.getClass())) {
        wasThrown = true;

        System.out.println("");
        e.printStackTrace(System.out);

        fail("Exception isn't expected here. Exception message: " + e.getMessage());
      } else {
        throw e;
      }
    } finally {
      if (shouldOccur && !wasThrown) {
        fail(exceptionCase.getAssertionErrorMessage());
      }
    }
  }

  public void checkPsiElementsAreStillValid(PsiFile psiFile) {
    if (psiFile == null || !psiFile.isValid() || isPerformanceTest()) return;

    psiFile.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        assertTrue(element.toString(), element.isValid());
        super.visitElement(element);
      }
    });
  }
}
