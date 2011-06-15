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

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.idea.Bombed;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;


/**
 * @author yole
 */
public class PlatformTestUtil {
  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));
  public static final CvsVirtualFileFilter CVS_FILE_FILTER = new CvsVirtualFileFilter();

  public static <T> void registerExtension(final ExtensionPointName<T> name, final T t, final Disposable parentDisposable) {
    registerExtension(Extensions.getRootArea(), name, t, parentDisposable);
  }

  public static <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> name, final T t, final Disposable parentDisposable) {
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(name.getName());
    extensionPoint.registerExtension(t);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(t);
      }
    });
  }

  protected static String toString(Object node, Queryable.PrintInfo printInfo) {
    if (node instanceof AbstractTreeNode) {
      if (printInfo != null) {
        return ((AbstractTreeNode)node).toTestString(printInfo);
      } else {
        return ((AbstractTreeNode)node).getTestPresentation();
      }
    }
    else if (node == null) {
      return "NULL";
    }
    else {
      return node.toString();
    }
  }

  public static String print(JTree tree, boolean withSelection) {
    return print(tree, withSelection, null);
  }

  public static String print(JTree tree, boolean withSelection, Condition<String> nodePrintCondition) {
    StringBuilder buffer = new StringBuilder();
    Object root = tree.getModel().getRoot();
    printImpl(tree, root, buffer, 0, withSelection, nodePrintCondition);
    return buffer.toString();
  }

  
  private static void printImpl(JTree tree, Object root, StringBuilder buffer, int level, boolean withSelection, @Nullable Condition<String> nodePrintCondition) {
    DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)root;


    final Object userObject = defaultMutableTreeNode.getUserObject();
    String nodeText;
    if (userObject != null) {
      nodeText = toString(userObject, null);
    }
    else {
      nodeText = String.valueOf(defaultMutableTreeNode);
    }


    if (nodePrintCondition != null && !nodePrintCondition.value(nodeText)) return;

    boolean expanded = tree.isExpanded(new TreePath(defaultMutableTreeNode.getPath()));
    StringUtil.repeatSymbol(buffer, ' ', level);
    if (expanded && !defaultMutableTreeNode.isLeaf()) {
      buffer.append("-");
    }

    if (!expanded && !defaultMutableTreeNode.isLeaf()) {
      buffer.append("+");
    }

    final boolean selected = tree.getSelectionModel().isPathSelected(new TreePath(defaultMutableTreeNode.getPath()));

    if (withSelection && selected) {
      buffer.append("[");
    }


    buffer.append(nodeText);

    if (withSelection && selected) {
      buffer.append("]");
    }

    buffer.append("\n");
    int childCount = tree.getModel().getChildCount(root);
    if (expanded) {
      for (int i = 0; i < childCount; i++) {
        printImpl(tree, tree.getModel().getChild(root, i), buffer, level + 1, withSelection, nodePrintCondition);
      }
    }
  }

  public static void assertTreeEqual(JTree tree, @NonNls String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqual(JTree tree, String expected, boolean checkSelected) {
    String treeStringPresentation = print(tree, checkSelected);
    Assert.assertEquals(expected, treeStringPresentation);
  }

  @TestOnly
  public static void waitForAlarm(final int delay) throws InterruptedException {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed(): "It's a bad idea to wait for an alarm under the write action. Somebody creates an alarm which requires read action and you are deadlocked.";
    assert ApplicationManager.getApplication().isDispatchThread();

    final AtomicBoolean invoked = new AtomicBoolean();
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            alarm.addRequest(new Runnable() {
              @Override
              public void run() {
                invoked.set(true);
              }
            }, delay);
          }
        });
      }
    }, delay);

    UIUtil.dispatchAllInvocationEvents();

    boolean sleptAlready = false;
    while (!invoked.get()) {
      UIUtil.dispatchAllInvocationEvents();
      Thread.sleep(sleptAlready ? 10 : delay);
      sleptAlready = true;
    }
    UIUtil.dispatchAllInvocationEvents();
  }

  @TestOnly
  public static void dispatchAllInvocationEventsInIdeEventQueue() throws InterruptedException {
    assert SwingUtilities.isEventDispatchThread() : Thread.currentThread();
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
        AWTEvent event1 = eventQueue.getNextEvent();
        if (event1 instanceof InvocationEvent) {
          IdeEventQueue.getInstance().dispatchEvent(event1);
        }
    }
  }

  private static Date raidDate(Bombed bombed) {
    final Calendar instance = Calendar.getInstance();
    instance.set(Calendar.YEAR, bombed.year());
    instance.set(Calendar.MONTH, bombed.month());
    instance.set(Calendar.DAY_OF_MONTH, bombed.day());
    instance.set(Calendar.HOUR_OF_DAY, bombed.time());
    instance.set(Calendar.MINUTE, 0);

    return instance.getTime();
  }

  public static boolean bombExplodes(Bombed bombedAnnotation) {
    Date now = new Date();
    return now.after(raidDate(bombedAnnotation));
  }

  public static boolean bombExplodes(int year, int month, int day, int h, int m, String who, String message) {
    final Calendar instance = Calendar.getInstance();
    instance.set(Calendar.YEAR, year);
    instance.set(Calendar.MONTH, month);
    instance.set(Calendar.DAY_OF_MONTH, day);
    instance.set(Calendar.HOUR_OF_DAY, h);
    instance.set(Calendar.MINUTE, m);
    Date time = instance.getTime();
    return isItMe(who) || new Date().after(time);
  }

  public static boolean isRotten(Bombed bomb) {
    long bombRotPeriod = 30L * 24 * 60 * 60 * 1000; // month
    return new Date().after(new Date(raidDate(bomb).getTime() + bombRotPeriod));
  }

  private static boolean isItMe(final String who) {
    return Comparing.equal(who, SystemProperties.getUserName(), false);
  }

  /**
   * @deprecated use {@link #print(com.intellij.ide.util.treeView.AbstractTreeStructure, Object, int, java.util.Comparator, int, char,
   *                               com.intellij.openapi.ui.Queryable.PrintInfo)}
   */
  public static StringBuffer print(AbstractTreeStructure structure,
                                   Object node,
                                   int currentLevel,
                                   Comparator comparator,
                                   int maxRowCount,
                                   char paddingChar) {

    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, null);
  }

  public static StringBuffer print(AbstractTreeStructure structure,
                                   Object node,
                                   int currentLevel,
                                   Comparator comparator,
                                   int maxRowCount,
                                   char paddingChar,
                                   Queryable.PrintInfo printInfo) {
    StringBuffer buffer = new StringBuffer();
    doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, 0, paddingChar, printInfo);
    return buffer;
  }

  private static int doPrint(StringBuffer buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             Comparator comparator,
                             int maxRowCount,
                             int currentLine,
                             char paddingChar) {
    return doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, currentLine, paddingChar, null);
  }

  private static int doPrint(StringBuffer buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             Comparator comparator,
                             int maxRowCount,
                             int currentLine,
                             char paddingChar,
                             Queryable.PrintInfo printInfo) {
    if (currentLine >= maxRowCount && maxRowCount != -1) return currentLine;

    StringUtil.repeatSymbol(buffer, paddingChar, currentLevel);
    buffer.append(toString(node, printInfo)).append("\n");
    currentLine++;
    Object[] children = structure.getChildElements(node);

    if (comparator != null) {
      ArrayList<?> list = new ArrayList<Object>(Arrays.asList(children));
      Collections.sort(list, comparator);
      children = ArrayUtil.toObjectArray(list);
    }
    for (Object child : children) {
      currentLine = doPrint(buffer, currentLevel + 1, child, structure, comparator, maxRowCount, currentLine, paddingChar, printInfo);
    }

    return currentLine;
  }

  public static String print(Object[] objects) {
    return print(Arrays.asList(objects));
  }

  public static String print(Collection c) {
    StringBuilder result = new StringBuilder();
    for (Iterator iterator = c.iterator(); iterator.hasNext();) {
      Object each = iterator.next();
      result.append(toString(each, null));
      if (iterator.hasNext()) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  public static String print(ListModel model) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < model.getSize(); i++) {
      result.append(toString(model.getElementAt(i), null));
      result.append("\n");
    }
    return result.toString();
  }

  public static String print(JTree tree) {
    return print(tree, false);
  }

  public static void assertTreeStructureEquals(final AbstractTreeStructure treeStructure, final String expected) {
    Assert.assertEquals(expected, print(treeStructure, treeStructure.getRootElement(), 0, null, -1, ' ').toString());
  }

  public static void invokeNamedAction(final String actionId) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    Assert.assertNotNull(action);
    final Presentation presentation = new Presentation();
    final AnActionEvent event =
        new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", presentation, ActionManager.getInstance(), 0);
    action.update(event);
    Assert.assertTrue(presentation.isEnabled());
    action.actionPerformed(event);
  }

  public static void assertTiming(final String message, final long expected, final long actual) {
    if (COVERAGE_ENABLED_BUILD) return;

    final long expectedOnMyMachine = Math.max(1, expected * Timings.MACHINE_TIMING / Timings.ETALON_TIMING);
    final double acceptableChangeFactor = 1.1;

    // Allow 10% more in case of test machine is busy.
    String logMessage = message;
    if (actual > expectedOnMyMachine) {
      int percentage = (int)(100.0 * (actual - expectedOnMyMachine) / expectedOnMyMachine);
      logMessage += ". Operation took " + percentage + "% longer than expected";
    }
    logMessage += ". Expected on my machine: " + expectedOnMyMachine + "." +
                  " Actual: " + actual + "." +
                  " Expected on Etalon machine: " + expected + ";" +
                  " Actual on Etalon: " + actual * Timings.ETALON_TIMING / Timings.MACHINE_TIMING + ";" +
                  " Timings: CPU=" + Timings.CPU_TIMING +
                  ", I/O=" + Timings.IO_TIMING + "." +
                  " (" + (int)(Timings.MACHINE_TIMING*1.0/Timings.ETALON_TIMING*100) + "% of the etalon)" +
                  ".";
    if (actual < expectedOnMyMachine) {
      TeamCityLogger.info(logMessage);
    }
    else if (actual < expectedOnMyMachine * acceptableChangeFactor) {
      TeamCityLogger.warning(logMessage, null);
    }
    else {
      // throw AssertionFailedError to try one more time
      throw new AssertionFailedError(logMessage);
    }
  }

  /**
   * example usage: startPerformanceTest(100, testRunnable).cpuBound().assertTiming();
   */
  public static TestInfo startPerformanceTest(int expected, @NotNull ThrowableRunnable test) {
    return startPerformanceTest("",expected, test);
  }
  public static TestInfo startPerformanceTest(@NonNls @NotNull String message, int expected, @NotNull ThrowableRunnable test) {
    return new TestInfo(test, expected,message);
  }

  // calculates average of the median values in the selected part of the array. E.g. for part=3 returns average in the middle third.
  public static long averageAmongMedians(@NotNull long[] time, int part) {
    assert part >= 1;
    int n = time.length;
    Arrays.sort(time);
    long total = 0;
    for (int i= n /2- n / part /2; i< n /2+ n / part /2; i++) {
      total += time[i];
    }
    return total/(n / part);
  }

  public static class TestInfo {
    private final ThrowableRunnable test; // runnable to measure
    private final int expected;           // millis the test is expected to run
    private ThrowableRunnable setup;      // to run before each test
    private boolean usesAllCPUCores;      // true if the test runs faster on multicore
    private int attempts = 4;             // number of retries if performance failed
    private final String message;         // to print on fail
    private boolean adjustForIO = true;   // true if test uses IO, timings need to be recalibrated according to this agent disk performance
    private boolean adjustForCPU = true;  // true if test uses CPU, timings need to be recalibrated according to this agent CPU speed

    private TestInfo(@NotNull ThrowableRunnable test, int expected, String message) {
      this.test = test;
      this.expected = expected;
      this.message = message;
    }

    public TestInfo setup(@NotNull ThrowableRunnable setup) { assert this.setup==null; this.setup = setup; return this; }
    public TestInfo usesAllCPUCores() { assert adjustForCPU : "This test configured to be io-bound, it cannot use all cores"; usesAllCPUCores = true; return this; }
    public TestInfo cpuBound() { adjustForIO = false; adjustForCPU = true; return this; }
    public TestInfo ioBound() { adjustForIO = true; adjustForCPU = false; return this; }
    public TestInfo attempts(int attempts) { this.attempts = attempts; return this; }

    public void assertTiming() {
      assert expected != 0 : "Must call .expect() before run test";
      if (COVERAGE_ENABLED_BUILD) return;

      while (true) {
        attempts--;
        long start;
        try {
          if (setup != null) setup.run();
          start = System.currentTimeMillis();
          test.run();
        }
        catch (Throwable throwable) {
          throw new RuntimeException(throwable);
        }
        long finish = System.currentTimeMillis();
        long duration = finish - start;

        int expectedOnMyMachine = expected;
        if (adjustForCPU) {
          // most of our algorithms are quadratic. sad but true.
          expectedOnMyMachine = Math.max(1, (int)(expectedOnMyMachine * (0.45 + Math.pow(1.0 * Timings.CPU_TIMING / Timings.ETALON_CPU_TIMING - 0.25,2))));
          expectedOnMyMachine = usesAllCPUCores ? expectedOnMyMachine * 8 / JobSchedulerImpl.CORES_COUNT : expectedOnMyMachine;
        }
        if (adjustForIO) {
          expectedOnMyMachine = Math.max(1, (int)(1.0 * expectedOnMyMachine * (1 + Math.pow(1.0 * Timings.IO_TIMING / Timings.ETALON_IO_TIMING - 1,2))));
        }
        final double acceptableChangeFactor = 1.1;

        // Allow 10% more in case of test machine is busy.
        String logMessage = message;
        if (duration > expectedOnMyMachine) {
          int percentage = (int)(100.0 * (duration - expectedOnMyMachine) / expectedOnMyMachine);
          logMessage += ". (" + percentage + "% longer).";
        }
        logMessage += " Expected: " + expectedOnMyMachine + "." +
                      " Actual: " + duration + "." + Timings.getStatistics() ;
        if (duration < expectedOnMyMachine) {
          int percentage = (int)(100.0 * (expectedOnMyMachine - duration) / expectedOnMyMachine);
          logMessage = "(" + percentage + "% faster). " + logMessage;

          TeamCityLogger.info(logMessage);
          System.out.println("SUCCESS: "+logMessage);
        }
        else if (duration < expectedOnMyMachine * acceptableChangeFactor) {
          TeamCityLogger.warning(logMessage, null);
          System.out.println("WARNING: " + logMessage);
        }
        else {
          // try one more time
          if (attempts == 0) throw new AssertionFailedError(logMessage);
          System.gc();
          System.gc();
          System.gc();
          String s = "Another epic fail (remaining attempts: " + attempts + "): " + logMessage;
          TeamCityLogger.warning(s, null);
          System.err.println(s);
          continue;
        }
        break;
      }
    }
  }


  public static void assertTiming(String message, long expected, @NotNull Runnable actionToMeasure) {
    assertTiming(message, expected, 4, actionToMeasure);
  }

  public static long measure(@NotNull Runnable actionToMeasure) {
    long start = System.currentTimeMillis();
    actionToMeasure.run();
    long finish = System.currentTimeMillis();
    return finish - start;
  }

  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      long duration = measure(actionToMeasure);
      try {
        assertTiming(message, expected, duration);
        break;
      }
      catch (AssertionFailedError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
        String s = "Another epic fail (remaining attempts: " + attempts + "): " + e.getMessage();
        TeamCityLogger.warning(s, null);
        System.err.println(s);
      }
    }
  }

  private static HashMap<String, VirtualFile> buildNameToFileMap(VirtualFile[] files, VirtualFileFilter filter) {
    HashMap<String, VirtualFile> map = new HashMap<String, VirtualFile>();
    for (VirtualFile file : files) {
      if (filter != null && !filter.accept(file)) continue;
      map.put(file.getName(), file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(VirtualFile dirAfter, VirtualFile dirBefore, VirtualFileFilter fileFilter) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();
    VirtualFile[] childrenAfter = dirAfter.getChildren();
    if (dirAfter.isInLocalFileSystem()) {
      File[] ioAfter = new File(dirAfter.getPath()).listFiles();
      shallowCompare(childrenAfter, ioAfter);
    }
    VirtualFile[] childrenBefore = dirBefore.getChildren();
    if (dirBefore.isInLocalFileSystem()) {
      File[] ioBefore = new File(dirBefore.getPath()).listFiles();
      shallowCompare(childrenBefore, ioBefore);
    }

    HashMap<String, VirtualFile> mapAfter = buildNameToFileMap(childrenAfter, fileFilter);
    HashMap<String, VirtualFile> mapBefore = buildNameToFileMap(childrenBefore, fileFilter);

    Set<String> keySetAfter = mapAfter.keySet();
    Set<String> keySetBefore = mapBefore.keySet();
    Assert.assertEquals(keySetAfter, keySetBefore);

    for (String name : keySetAfter) {
      VirtualFile fileAfter = mapAfter.get(name);
      VirtualFile fileBefore = mapBefore.get(name);
      if (fileAfter.isDirectory()) {
        assertDirectoriesEqual(fileAfter, fileBefore, fileFilter);
      }
      else {
        assertFilesEqual(fileAfter, fileBefore);
      }
    }
  }

  private static void shallowCompare(final VirtualFile[] vfs, final File[] io) {
    List<String> vfsPaths = new ArrayList<String>();
    for (VirtualFile file : vfs) {
      vfsPaths.add(file.getPath());
    }

    List<String> ioPaths = new ArrayList<String>();
    for (File file : io) {
      ioPaths.add(file.getPath().replace(File.separatorChar, '/'));
    }

    Assert.assertEquals(sortAndJoin(vfsPaths), sortAndJoin(ioPaths));
  }

  private static String sortAndJoin(List<String> strings) {
    Collections.sort(strings);
    StringBuilder buf = new StringBuilder();
    for (String string : strings) {
      buf.append(string);
      buf.append('\n');
    }
    return buf.toString();
  }

  public static void assertFilesEqual(VirtualFile fileAfter, VirtualFile fileBefore) throws IOException {
    try {
      assertJarFilesEqual(VfsUtil.virtualToIoFile(fileAfter), VfsUtil.virtualToIoFile(fileBefore));
    }
    catch (IOException e) {
      FileDocumentManager manager = FileDocumentManager.getInstance();
      Document docBefore = manager.getDocument(fileBefore);
      boolean canLoadBeforeText = !fileBefore.getFileType().isBinary() || fileBefore.getFileType() == FileTypes.UNKNOWN;
      String textB = docBefore == null ? !canLoadBeforeText ? null : LoadTextUtil.getTextByBinaryPresentation(fileBefore.contentsToByteArray(false), fileBefore).toString() : docBefore.getText();
      Document docAfter = manager.getDocument(fileAfter);
      boolean canLoadAfterText = !fileBefore.getFileType().isBinary() || fileBefore.getFileType() == FileTypes.UNKNOWN;
      String textA = docAfter == null ? !canLoadAfterText ? null : LoadTextUtil.getTextByBinaryPresentation(fileAfter.contentsToByteArray(false), fileAfter).toString() : docAfter.getText();
      if (textA != null && textB != null) {
        Assert.assertEquals(fileAfter.getPath(), textA, textB);
      }
      else {
        Assert.assertArrayEquals(fileAfter.getPath(), fileAfter.contentsToByteArray(), fileBefore.contentsToByteArray());
      }
    }
  }

  public static void assertJarFilesEqual(File file1, File file2) throws IOException {
    JarFile jarFile1 = null;
    JarFile jarFile2 = null;
    final File tempDirectory1;
    final File tempDirectory2;
    try {
      jarFile2 = new JarFile(file2);
      jarFile1 = new JarFile(file1);
      tempDirectory1 = PlatformTestCase.createTempDir("tmp1");
      tempDirectory2 = PlatformTestCase.createTempDir("tmp2");
      ZipUtil.extract(jarFile1, tempDirectory1, CVS_FILE_FILTER);
      ZipUtil.extract(jarFile2, tempDirectory2, CVS_FILE_FILTER);
    }
    finally {
      if (jarFile1 != null) {
        jarFile1.close();
      }
      if (jarFile2 != null) {
        jarFile2.close();
      }
    }
    final VirtualFile dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory1);
    final VirtualFile dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory2);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        dirAfter.refresh(false, true);
        dirBefore.refresh(false, true);
      }
    });
    assertDirectoriesEqual(dirAfter, dirBefore, CVS_FILE_FILTER);
  }

  public static void assertElementsEqual(final Element expected, final Element actual) throws IOException {
    if (!JDOMUtil.areElementsEqual(expected, actual)) {
      junit.framework.Assert.assertEquals(printElement(expected), printElement(actual));
    }
  }

  public static String printElement(final Element element) throws IOException {
    final StringWriter writer = new StringWriter();
    JDOMUtil.writeElement(element, writer, "\n");
    return writer.getBuffer().toString();
  }

  public static class CvsVirtualFileFilter implements VirtualFileFilter, FilenameFilter {
    @Override
    public boolean accept(VirtualFile file) {
      return !file.isDirectory() || !"CVS".equals(file.getName());
    }

    @Override
    public boolean accept(File dir, String name) {
      return !name.contains("CVS");
    }
  }
}
