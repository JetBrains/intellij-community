// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.Equality;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static org.junit.Assert.*;

/**
 * @author yole
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "TestOnlyProblems"})
public class PlatformTestUtil {
  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));

  private static final List<Runnable> ourProjectCleanups = new CopyOnWriteArrayList<>();
  private static final long MAX_WAIT_TIME = TimeUnit.MINUTES.toMillis(2);

  @NotNull
  public static String getTestName(@NotNull String name, boolean lowercaseFirstLetter) {
    name = StringUtil.trimStart(name, "test");
    return StringUtil.isEmpty(name) ? "" : lowercaseFirstLetter(name, lowercaseFirstLetter);
  }

  @NotNull
  public static String lowercaseFirstLetter(@NotNull String name, boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  public static boolean isAllUppercaseName(@NotNull String name) {
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

  public static <T> void registerExtension(@NotNull ExtensionPointName<T> name, @NotNull T t, @NotNull Disposable parentDisposable) {
    registerExtension(Extensions.getRootArea(), name, t, parentDisposable);
  }

  public static <T> void registerExtension(@NotNull ExtensionsArea area,
                                           @NotNull ExtensionPointName<T> name,
                                           @NotNull T t,
                                           @NotNull Disposable parentDisposable) {
    ExtensionPoint<T> extensionPoint = area.getExtensionPoint(name.getName());
    extensionPoint.registerExtension(t);
    Disposer.register(parentDisposable, () -> extensionPoint.unregisterExtension(t));
  }

  public static <T> void unregisterAllExtensions(@NotNull ExtensionPointName<T> name, @NotNull Disposable parentDisposable) {
    ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name.getName());
    T[] extensions = name.getExtensions();
    Arrays.stream(extensions).forEach(extensionPoint::unregisterExtension);
    Disposer.register(parentDisposable, () -> Arrays.stream(extensions).forEach(extensionPoint::registerExtension));
  }

  @Nullable
  public static String toString(@Nullable Object node, @Nullable Queryable.PrintInfo printInfo) {
    if (node instanceof AbstractTreeNode) {
      if (printInfo != null) {
        return ((AbstractTreeNode)node).toTestString(printInfo);
      }
      else {
        @SuppressWarnings({"deprecation", "UnnecessaryLocalVariable"})
        final String presentation = ((AbstractTreeNode)node).getTestPresentation();
        return presentation;
      }
    }
    return String.valueOf(node);
  }

  public static String print(JTree tree, boolean withSelection) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, null);
  }

  public static String print(JTree tree, TreePath path, @Nullable Queryable.PrintInfo printInfo, boolean withSelection) {
    return print(tree, path,  withSelection, printInfo, null);
  }

  public static String print(JTree tree, boolean withSelection, @Nullable Predicate<? super String> nodePrintCondition) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, nodePrintCondition);
  }

  private static String print(JTree tree, TreePath path,
                             boolean withSelection,
                             @Nullable Queryable.PrintInfo printInfo,
                             @Nullable Predicate<? super String> nodePrintCondition) {
    return StringUtil.join(printAsList(tree, path, withSelection, printInfo, nodePrintCondition), "\n");
  }

  private static Collection<String> printAsList(JTree tree,
                                                TreePath path,
                                                boolean withSelection,
                                                @Nullable Queryable.PrintInfo printInfo,
                                                @Nullable Predicate<? super String> nodePrintCondition) {
    Collection<String> strings = new ArrayList<>();
    printImpl(tree, path, strings, 0, withSelection, printInfo, nodePrintCondition);
    return strings;
  }

  private static void printImpl(JTree tree,
                                TreePath path,
                                Collection<? super String> strings,
                                int level,
                                boolean withSelection,
                                @Nullable Queryable.PrintInfo printInfo,
                                @Nullable Predicate<? super String> nodePrintCondition) {
    Object pathComponent = path.getLastPathComponent();
    Object userObject = TreeUtil.getUserObject(pathComponent);
    String nodeText = toString(userObject, printInfo);

    if (nodePrintCondition != null && !nodePrintCondition.test(nodeText)) {
      return;
    }

    StringBuilder buff = new StringBuilder();
    StringUtil.repeatSymbol(buff, ' ', level);

    boolean expanded = tree.isExpanded(path);
    int childCount = tree.getModel().getChildCount(pathComponent);
    if (childCount > 0) {
      buff.append(expanded ? "-" : "+");
    }

    boolean selected = tree.getSelectionModel().isPathSelected(path);
    if (withSelection && selected) {
      buff.append("[");
    }

    buff.append(nodeText);

    if (withSelection && selected) {
      buff.append("]");
    }

    strings.add(buff.toString());

    if (expanded) {
      for (int i = 0; i < childCount; i++) {
        TreePath childPath = path.pathByAddingChild(tree.getModel().getChild(pathComponent, i));
        printImpl(tree, childPath, strings, level + 1, withSelection, printInfo, nodePrintCondition);
      }
    }
  }

  public static void assertTreeEqual(JTree tree, @NonNls String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqual(JTree tree, String expected, boolean checkSelected) {
    String treeStringPresentation = print(tree, checkSelected);
    assertEquals(expected.trim(), treeStringPresentation.trim());
  }

  public static void expand(JTree tree, int... rows) {
    for (int row : rows) {
      tree.expandRow(row);
      waitWhileBusy(tree);
    }
  }

  public static void expandAll(JTree tree) {
    waitForPromise(TreeUtil.promiseExpandAll(tree));
  }

  private static long getMillisSince(long startTimeMillis) {
    return System.currentTimeMillis() - startTimeMillis;
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis) {
    assertMaxWaitTimeSince(startTimeMillis, MAX_WAIT_TIME);
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis, long timeout) {
    assert getMillisSince(startTimeMillis) <= timeout : "the waiting takes too long";
  }

  private static void assertDispatchThreadWithoutWriteAccess() {
    assertDispatchThreadWithoutWriteAccess(getApplication());
  }

  private static void assertDispatchThreadWithoutWriteAccess(Application application) {
    if (application != null) {
      assert !application.isWriteAccessAllowed() : "do not wait under the write action to avoid possible deadlock";
      assert application.isDispatchThread();
    }
    else {
      // do not check for write access in simple tests
      assert EventQueue.isDispatchThread();
    }
  }

  private static boolean isBusy(JTree tree) {
    UIUtil.dispatchAllInvocationEvents();
    TreeModel model = tree.getModel();
    if (model instanceof AsyncTreeModel) {
      AsyncTreeModel async = (AsyncTreeModel)model;
      if (async.isProcessing()) return true;
      UIUtil.dispatchAllInvocationEvents();
      return async.isProcessing();
    }
    AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
    if (builder == null) return false;
    AbstractTreeUi ui = builder.getUi();
    if (ui == null) return false;
    return ui.hasPendingWork();
  }

  public static void waitWhileBusy(JTree tree) {
    assertDispatchThreadWithoutWriteAccess();
    long startTimeMillis = System.currentTimeMillis();
    while (isBusy(tree)) {
      assertMaxWaitTimeSince(startTimeMillis);
    }
  }

  public static void waitForCallback(@NotNull ActionCallback callback) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    callback.doWhenDone(() -> promise.setResult(null));
    waitForPromise(promise);
  }

  @Nullable
  public static <T> T waitForPromise(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME);
  }

  @Nullable
  public static <T> T waitForPromise(@NotNull Promise<T> promise, long timeout) {
    assertDispatchThreadWithoutWriteAccess();
    AtomicBoolean complete = new AtomicBoolean(false);
    promise.onProcessed(ignore -> complete.set(true));
    T result = null;
    long start = System.currentTimeMillis();
    do {
      UIUtil.dispatchAllInvocationEvents();
      try {
        result = promise.blockingGet(20, TimeUnit.MILLISECONDS);
      }
      catch (Exception ignore) {
      }
      assertMaxWaitTimeSince(start, timeout);
    }
    while (!complete.get());
    UIUtil.dispatchAllInvocationEvents();
    return result;
  }

  public static void waitForAlarm(final int delay) {
    @NotNull Application app = getApplication();
    assertDispatchThreadWithoutWriteAccess();

    Disposable tempDisposable = Disposer.newDisposable();

    final AtomicBoolean runnableInvoked = new AtomicBoolean();
    final AtomicBoolean pooledRunnableInvoked = new AtomicBoolean();
    final AtomicBoolean alarmInvoked1 = new AtomicBoolean();
    final AtomicBoolean alarmInvoked2 = new AtomicBoolean();
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    final Alarm pooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, tempDisposable);
    ModalityState initialModality = ModalityState.current();

    alarm.addRequest(() -> {
      alarmInvoked1.set(true);
      app.invokeLater(() -> {
        runnableInvoked.set(true);
        alarm.addRequest(() -> alarmInvoked2.set(true), delay);
      });
    }, delay);
    pooledAlarm.addRequest(() -> pooledRunnableInvoked.set(true), delay);

    UIUtil.dispatchAllInvocationEvents();

    long start = System.currentTimeMillis();
    try {
      boolean sleptAlready = false;
      while (!alarmInvoked2.get()) {
        AtomicBoolean laterInvoked = new AtomicBoolean();
        app.invokeLater(() -> laterInvoked.set(true));
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(laterInvoked.get());

        TimeoutUtil.sleep(sleptAlready ? 10 : delay);
        sleptAlready = true;
        if (getMillisSince(start) > MAX_WAIT_TIME) {
          String queue = ((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).dumpQueue();
          throw new AssertionError("Couldn't await alarm" +
                                   "; alarm passed=" + alarmInvoked1.get() +
                                   "; modality1=" + initialModality +
                                   "; modality2=" + ModalityState.current() +
                                   "; non-modal=" + (initialModality == ModalityState.NON_MODAL) +
                                   "; invokeLater passed=" + runnableInvoked.get() +
                                   "; pooled alarm passed=" + pooledRunnableInvoked.get() +
                                   "; app.disposed=" + app.isDisposed() +
                                   "; alarm.disposed=" + alarm.isDisposed() +
                                   "; alarm.requests=" + alarm.getActiveRequestCount() +
                                   "\n delayQueue=" + StringUtil.trimLog(queue, 1000) +
                                   "\n invocatorQueue=" + LaterInvocator.getLaterInvocatorQueue()
          );
        }
      }
    }
    finally {
      Disposer.dispose(tempDisposable);
    }
    UIUtil.dispatchAllInvocationEvents();
  }

  /**
   * Dispatch all pending invocation events (if any) in the {@link IdeEventQueue}, ignores and removes all other events from the queue.
   * Should only be invoked in Swing thread (asserted inside {@link IdeEventQueue#dispatchEvent(AWTEvent)})
   */
  public static void dispatchAllInvocationEventsInIdeEventQueue() {
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        event = eventQueue.getNextEvent();
        if (event instanceof InvocationEvent) {
          eventQueue.dispatchEvent(event);
        }
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Dispatch all pending events (if any) in the {@link IdeEventQueue}.
   * Should only be invoked in Swing thread (asserted inside {@link IdeEventQueue#dispatchEvent(AWTEvent)})
   */
  public static void dispatchAllEventsInIdeEventQueue() throws InterruptedException {
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    //noinspection StatementWithEmptyBody
    while (dispatchNextEventIfAny(eventQueue) != null);
  }

  /**
   * Dispatch one pending event (if any) in the {@link IdeEventQueue}.
   * Should only be invoked in Swing thread (asserted inside {@link IdeEventQueue#dispatchEvent(AWTEvent)})
   */
  public static AWTEvent dispatchNextEventIfAny(@NotNull IdeEventQueue eventQueue) throws InterruptedException {
    assert SwingUtilities.isEventDispatchThread() : Thread.currentThread();
    AWTEvent event = eventQueue.peekEvent();
    if (event == null) return null;
    AWTEvent event1 = eventQueue.getNextEvent();
    eventQueue.dispatchEvent(event1);
    return event1;
  }

  public static StringBuilder print(AbstractTreeStructure structure, Object node, int currentLevel, @Nullable Comparator comparator,
                                    int maxRowCount, char paddingChar, @Nullable Queryable.PrintInfo printInfo) {
    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, o -> toString(o, printInfo));
  }

  public static String print(AbstractTreeStructure structure, Object node, Function<Object, String> nodePresenter) {
    return print(structure, node, 0, Comparator.comparing(nodePresenter), -1, ' ', nodePresenter).toString();
  }

  private static StringBuilder print(AbstractTreeStructure structure, Object node, int currentLevel, @Nullable Comparator comparator,
                                     int maxRowCount, char paddingChar, Function<Object, String> nodePresenter) {
    StringBuilder buffer = new StringBuilder();
    doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, 0, paddingChar, nodePresenter);
    return buffer;
  }

  private static int doPrint(StringBuilder buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             @Nullable Comparator comparator,
                             int maxRowCount,
                             int currentLine,
                             char paddingChar,
                             Function<Object, String> nodePresenter) {
    if (currentLine >= maxRowCount && maxRowCount != -1) return currentLine;

    StringUtil.repeatSymbol(buffer, paddingChar, currentLevel);
    buffer.append(nodePresenter.apply(node)).append("\n");
    currentLine++;
    Object[] children = structure.getChildElements(node);

    if (comparator != null) {
      ArrayList<?> list = new ArrayList<>(Arrays.asList(children));
      @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked"}) Comparator<Object> c = comparator;
      Collections.sort(list, c);
      children = ArrayUtil.toObjectArray(list);
    }
    for (Object child : children) {
      currentLine = doPrint(buffer, currentLevel + 1, child, structure, comparator, maxRowCount, currentLine, paddingChar, nodePresenter);
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

  public static void invokeNamedAction(final String actionId) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    assertNotNull(action);
    final Presentation presentation = new Presentation();
    @SuppressWarnings("deprecation") final DataContext context = DataManager.getInstance().getDataContext();
    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);
    action.beforeActionPerformedUpdate(event);
    assertTrue(presentation.isEnabled());
    action.actionPerformed(event);
  }

  public static void assertTiming(final String message, final long expectedMs, final long actual) {
    if (COVERAGE_ENABLED_BUILD) return;

    long expectedOnMyMachine = Math.max(1, expectedMs * Timings.CPU_TIMING / Timings.REFERENCE_CPU_TIMING);

    // Allow 10% more in case of test machine is busy.
    String logMessage = message;
    if (actual > expectedOnMyMachine) {
      int percentage = (int)(100.0 * (actual - expectedOnMyMachine) / expectedOnMyMachine);
      logMessage += ". Operation took " + percentage + "% longer than expected";
    }
    logMessage += ". Expected on my machine: " + expectedOnMyMachine + "." +
                  " Actual: " + actual + "." +
                  " Expected on Standard machine: " + expectedMs + ";" +
                  " Timings: CPU=" + Timings.CPU_TIMING +
                  ", I/O=" + Timings.IO_TIMING + ".";
    final double acceptableChangeFactor = 1.1;
    if (actual < expectedOnMyMachine) {
      System.out.println(logMessage);
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
   * example usage: {@code startPerformanceTest("calculating pi",100, testRunnable).assertTiming();}
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public static PerformanceTestInfo startPerformanceTest(@NonNls @NotNull String what, int expectedMs, @NotNull ThrowableRunnable test) {
    return new PerformanceTestInfo(test, expectedMs, what);
  }

  public static void assertPathsEqual(@Nullable String expected, @Nullable String actual) {
    if (expected != null) expected = FileUtil.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtil.toSystemIndependentName(actual);
    assertEquals(expected, actual);
  }

  @NotNull
  public static String getJavaExe() {
    return SystemProperties.getJavaHome() + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");
  }

  @NotNull
  public static String getRtJarPath() {
    return SystemProperties.getJavaHome() + "/lib/rt.jar";
  }

  @NotNull
  public static URL getRtJarURL() {
    String home = SystemProperties.getJavaHome();
    try {
      return JavaVersion.current().feature >= 9 ? new URL("jrt:" + home) : new File(home + "/lib/rt.jar").toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void forceCloseProjectWithoutSaving(@NotNull Project project) {
    ProjectManagerEx.getInstanceEx().forceCloseProject(project, false /* do not dispose */);
    // explicitly dispose because `dispose` option for forceCloseProject doesn't work todo why?
    getApplication().runWriteAction(() -> Disposer.dispose(project));
  }

  public static void saveProject(@NotNull Project project) {
    saveProject(project, false);
  }

  public static void saveProject(@NotNull Project project, boolean isForceSavingAllSettings) {
    ProjectManagerEx.getInstanceEx().flushChangedProjectFileAlarm();
    StateStorageManagerKt.saveComponentManager(project, isForceSavingAllSettings);
  }

  static void waitForAllBackgroundActivityToCalmDown() {
    for (int i = 0; i < 50; i++) {
      CpuUsageData data = CpuUsageData.measureCpuUsage(() -> TimeoutUtil.sleep(100));
      if (!data.hasAnyActivityBesides(Thread.currentThread())) {
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

  @SuppressWarnings("CallToSystemGC")
  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      waitForAllBackgroundActivityToCalmDown();
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
        String s = e.getMessage() + "\n  " + attempts + " attempts remain";
        TeamCityLogger.warning(s, null);
        System.err.println(s);
      }
    }
  }

  private static Map<String, VirtualFile> buildNameToFileMap(VirtualFile[] files, @Nullable VirtualFileFilter filter) {
    Map<String, VirtualFile> map = new HashMap<>();
    for (VirtualFile file : files) {
      if (filter != null && !filter.accept(file)) continue;
      map.put(file.getName(), file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(VirtualFile dirExpected, VirtualFile dirActual) throws IOException {
    assertDirectoriesEqual(dirExpected, dirActual, null);
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  public static void assertDirectoriesEqual(VirtualFile dirExpected, VirtualFile dirActual, @Nullable VirtualFileFilter fileFilter) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile[] childrenAfter = dirExpected.getChildren();
    shallowCompare(dirExpected, childrenAfter);

    VirtualFile[] childrenBefore = dirActual.getChildren();
    shallowCompare(dirActual, childrenBefore);

    Map<String, VirtualFile> mapAfter = buildNameToFileMap(childrenAfter, fileFilter);
    Map<String, VirtualFile> mapBefore = buildNameToFileMap(childrenBefore, fileFilter);

    Set<String> keySetAfter = mapAfter.keySet();
    Set<String> keySetBefore = mapBefore.keySet();
    assertEquals(dirExpected.getPath(), keySetAfter, keySetBefore);

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

  private static void shallowCompare(VirtualFile dir, VirtualFile[] vfs) {
    if (dir.isInLocalFileSystem() && dir.getFileSystem() != TempFileSystem.getInstance()) {
      String vfsPaths = Stream.of(vfs).map(VirtualFile::getPath).sorted().collect(Collectors.joining("\n"));
      File[] io = notNull(new File(dir.getPath()).listFiles());
      String ioPaths = Stream.of(io).map(f -> FileUtil.toSystemIndependentName(f.getPath())).sorted().collect(Collectors.joining("\n"));
      assertEquals(vfsPaths, ioPaths);
    }
  }

  public static void assertFilesEqual(VirtualFile fileExpected, VirtualFile fileActual) throws IOException {
    try {
      assertJarFilesEqual(VfsUtilCore.virtualToIoFile(fileExpected), VfsUtilCore.virtualToIoFile(fileActual));
    }
    catch (IOException e) {
      String actual = fileText(fileActual);
      String expected = fileText(fileExpected);
      if (expected == null || actual == null) {
        assertArrayEquals(fileExpected.getPath(), fileExpected.contentsToByteArray(), fileActual.contentsToByteArray());
      }
      else if (!StringUtil.equals(expected, actual)) {
        throw new FileComparisonFailure("Text mismatch in file " + fileExpected.getName(), expected, actual, fileExpected.getPath());
      }
    }
  }

  private static String fileText(VirtualFile file) throws IOException {
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    if (doc != null) {
      return doc.getText();
    }
    if (!file.getFileType().isBinary() || file.getFileType() == FileTypes.UNKNOWN) {
      return LoadTextUtil.getTextByBinaryPresentation(file.contentsToByteArray(false), file).toString();
    }
    return null;
  }

  private static void assertJarFilesEqual(File file1, File file2) throws IOException {
    final File tempDir = FileUtilRt.createTempDirectory("assert_jar_tmp", null, false);
    try {
      final File tempDirectory1 = new File(tempDir, "tmp1");
      final File tempDirectory2 = new File(tempDir, "tmp2");
      FileUtilRt.createDirectory(tempDirectory1);
      FileUtilRt.createDirectory(tempDirectory2);

      try (JarFile jarFile1 = new JarFile(file1)) {
        try (JarFile jarFile2 = new JarFile(file2)) {
          new Decompressor.Zip(new File(jarFile1.getName())).extract(tempDirectory1);
          new Decompressor.Zip(new File(jarFile2.getName())).extract(tempDirectory2);
        }
      }

      final VirtualFile dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory1);
      assertNotNull(tempDirectory1.toString(), dirAfter);
      final VirtualFile dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory2);
      assertNotNull(tempDirectory2.toString(), dirBefore);
      getApplication().runWriteAction(() -> {
        dirAfter.refresh(false, true);
        dirBefore.refresh(false, true);
      });
      assertDirectoriesEqual(dirAfter, dirBefore);
    }
    finally {
      FileUtilRt.delete(tempDir);
    }
  }

  public static String getCommunityPath() {
    final String homePath = IdeaTestExecutionPolicy.getHomePathWithPolicy();
    if (new File(homePath, "community/.idea").isDirectory()) {
      return homePath + File.separatorChar + "community";
    }
    return homePath;
  }

  public static String getPlatformTestDataPath() {
    return getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/";
  }

  public static Comparator<AbstractTreeNode> createComparator(final Queryable.PrintInfo printInfo) {
    return (o1, o2) -> {
      String displayText1 = o1.toTestString(printInfo);
      String displayText2 = o2.toTestString(printInfo);
      return Comparing.compare(displayText1, displayText2);
    };
  }

  @NotNull
  public static <T> T notNull(@Nullable T t) {
    assertNotNull(t);
    return t;
  }

  @NotNull
  public static String loadFileText(@NotNull String fileName) throws IOException {
    return StringUtil.convertLineSeparators(FileUtil.loadFile(new File(fileName)));
  }

  public static void withEncoding(@NotNull String encoding, @NotNull ThrowableRunnable r) {
    try {
      Charset oldCharset = Charset.defaultCharset();
      try {
        patchSystemFileEncoding(encoding);
        r.run();
      }
      finally {
        patchSystemFileEncoding(oldCharset.name());
      }
    }
    catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private static void patchSystemFileEncoding(String encoding) {
    ReflectionUtil.resetField(Charset.class, Charset.class, "defaultCharset");
    System.setProperty("file.encoding", encoding);
  }

  public static void withStdErrSuppressed(@NotNull Runnable r) {
    PrintStream std = System.err;
    System.setErr(new PrintStream(NULL));
    try {
      r.run();
    }
    finally {
      System.setErr(std);
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static final OutputStream NULL = new OutputStream() {
    @Override
    public void write(int b) { }
  };

  public static void assertSuccessful(@NotNull GeneralCommandLine command) {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(command.withRedirectErrorStream(true));
      assertEquals(output.getStdout(), 0, output.getExitCode());
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static List<WebReference> collectWebReferences(@NotNull PsiElement element) {
    List<WebReference> refs = new ArrayList<>();
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        for (PsiReference ref : element.getReferences()) {
          if (ref instanceof WebReference) {
            refs.add((WebReference)ref);
          }
        }
        super.visitElement(element);
      }
    });
    return refs;
  }

  public static void registerProjectCleanup(@NotNull Runnable cleanup) {
    ourProjectCleanups.add(cleanup);
  }

  public static void cleanupAllProjects() {
    for (Runnable each : ourProjectCleanups) {
      each.run();
    }
    ourProjectCleanups.clear();
  }

  /**
   * Disposes the application (it also stops some application-related threads)
   * and checks for project leaks.
   */
  public static void disposeApplicationAndCheckForProjectLeaks() {
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        LightPlatformTestCase.initApplication(); // in case nobody cared to init. LightPlatformTestCase.disposeApplication() would not work otherwise.
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      cleanupAllProjects();

      UIUtil.dispatchAllInvocationEvents();

      ApplicationImpl application = (ApplicationImpl)getApplication();
      System.out.println(application.writeActionStatistics());
      System.out.println(ActionUtil.ActionPauses.STAT.statistics());
      System.out.println(((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).statistics());
      System.out.println("ProcessIOExecutorService threads created: " + ((ProcessIOExecutorService)ProcessIOExecutorService.INSTANCE).getThreadCounter());

      try {
        LeakHunter.checkNonDefaultProjectLeak();
      }
      catch (AssertionError | Exception e) {
        captureMemorySnapshot();
        ExceptionUtil.rethrow(e);
      }
      finally {
        application.setDisposeInProgress(true);
        LightPlatformTestCase.disposeApplication();
        UIUtil.dispatchAllInvocationEvents();
      }
    });
  }

  public static void captureMemorySnapshot() {
    try {
      Method snapshot = ReflectionUtil.getMethod(Class.forName("com.intellij.util.ProfilingUtil"), "captureMemorySnapshot");
      if (snapshot != null) {
        Object path = snapshot.invoke(null);
        System.out.println("Memory snapshot captured to '" + path + "'");
      }
    }
    catch (ClassNotFoundException e) {
      // ProfilingUtil is missing from the classpath, ignore
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }

  public static <T> void assertComparisonContractNotViolated(@NotNull List<? extends T> values,
                                                             @NotNull Comparator<? super T> comparator,
                                                             @NotNull Equality<? super T> equality) {
    for (int i1 = 0; i1 < values.size(); i1++) {
      for (int i2 = i1; i2 < values.size(); i2++) {
        T value1 = values.get(i1);
        T value2 = values.get(i2);

        int result12 = comparator.compare(value1, value2);
        int result21 = comparator.compare(value2, value1);
        if (equality.equals(value1, value2)) {
          assertEquals(String.format("Equal, but not 0: '%s' - '%s'", value1, value2), 0, result12);
          assertEquals(String.format("Equal, but not 0: '%s' - '%s'", value2, value1), 0, result21);
        }
        else {
          if (result12 == 0) fail(String.format("Not equal, but 0: '%s' - '%s'", value1, value2));
          if (result21 == 0) fail(String.format("Not equal, but 0: '%s' - '%s'", value2, value1));
          if (Integer.signum(result12) == Integer.signum(result21)) {
            fail(String.format("Not symmetrical: '%s' - '%s'", value1, value2));
          }
        }

        for (int i3 = i2; i3 < values.size(); i3++) {
          T value3 = values.get(i3);

          int result23 = comparator.compare(value2, value3);
          int result31 = comparator.compare(value3, value1);

          if (!isTransitive(result12, result23, result31)) {
            fail(String.format("Not transitive: '%s' - '%s' - '%s'", value1, value2, value3));
          }
        }
      }
    }
  }

  private static boolean isTransitive(int result12, int result23, int result31) {
    if (result12 == 0 && result23 == 0 && result31 == 0) return true;

    if (result12 > 0 && result23 > 0 && result31 > 0) return false;
    if (result12 < 0 && result23 < 0 && result31 < 0) return false;

    if (result12 == 0 && Integer.signum(result23) * Integer.signum(result31) >= 0) return false;
    if (result23 == 0 && Integer.signum(result12) * Integer.signum(result31) >= 0) return false;
    if (result31 == 0 && Integer.signum(result23) * Integer.signum(result12) >= 0) return false;

    return true;
  }

  public static void setLongMeaninglessFileIncludeTemplateTemporarilyFor(@NotNull Project project, @NotNull Disposable parentDisposable) {
    FileTemplateManagerImpl templateManager = (FileTemplateManagerImpl)FileTemplateManager.getInstance(project);
    templateManager.setDefaultFileIncludeTemplateTextTemporarilyForTest(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME,
    "/**\n" +
    " * Created by ${USER} on ${DATE}.\n" +
    " */\n", parentDisposable);
  }
}