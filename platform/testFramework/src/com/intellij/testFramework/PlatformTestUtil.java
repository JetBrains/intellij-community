// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.UrlReference;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author yole
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "TestOnlyProblems"})
public final class PlatformTestUtil {
  private static final Logger LOG = Logger.getInstance(PlatformTestUtil.class);
  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));

  private static final List<Runnable> ourProjectCleanups = new CopyOnWriteArrayList<>();
  private static final long MAX_WAIT_TIME = TimeUnit.MINUTES.toMillis(2);

  public static @NotNull String getTestName(@NotNull String name, boolean lowercaseFirstLetter) {
    name = StringUtil.trimStart(name, "test");
    return StringUtil.isEmpty(name) ? "" : lowercaseFirstLetter(name, lowercaseFirstLetter);
  }

  public static @NotNull String lowercaseFirstLetter(@NotNull String name, boolean lowercaseFirstLetter) {
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

  /**
   * @see ExtensionPointImpl#maskAll(List, Disposable, boolean)
   */
  public static <T> void maskExtensions(@NotNull ProjectExtensionPointName<T> pointName,
                                        @NotNull Project project,
                                        @NotNull List<T> newExtensions,
                                        @NotNull Disposable parentDisposable) {
    ((ExtensionPointImpl<T>)pointName.getPoint(project)).maskAll(newExtensions, parentDisposable, true);
  }

  public static @Nullable String toString(@Nullable Object node, @Nullable Queryable.PrintInfo printInfo) {
    if (node instanceof AbstractTreeNode) {
      if (printInfo != null) {
        return ((AbstractTreeNode<?>)node).toTestString(printInfo);
      }
      else {
        //noinspection deprecation
        return ((AbstractTreeNode<?>)node).getTestPresentation();
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

  public static void assertTreeEqual(@NotNull JTree tree, @NonNls String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqual(@NotNull JTree tree, String expected, boolean checkSelected) {
    assertTreeEqual(tree, expected, checkSelected, false);
  }

  public static void assertTreeEqual(@NotNull JTree tree, @NotNull String expected, boolean checkSelected, boolean ignoreOrder) {
    String treeStringPresentation = print(tree, checkSelected);
    if (ignoreOrder) {
      for (String line : treeStringPresentation.split("\n")) {
        if (!expected.contains(line + "\n")) {
          fail("Missing node: " + line);
        }
      }
    }
    else {
      assertEquals(expected.trim(), treeStringPresentation.trim());
    }
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
    long took = getMillisSince(startTimeMillis);
    assert took <= timeout : String.format("the waiting takes too long. Expected to take no more than: %d ms but took: %d ms", timeout, took);
  }

  private static void assertDispatchThreadWithoutWriteAccess() {
    assertDispatchThreadWithoutWriteAccess(ApplicationManager.getApplication());
  }

  private static void assertDispatchThreadWithoutWriteAccess(Application application) {
    if (application != null) {
      assert !application.isWriteAccessAllowed() : "do not wait under write action to avoid possible deadlock";
      assert application.isDispatchThread();
    }
    else {
      // do not check for write access in simple tests
      assert EventQueue.isDispatchThread();
    }
  }

  @SuppressWarnings("deprecation")
  private static boolean isBusy(JTree tree, TreeModel model) {
    UIUtil.dispatchAllInvocationEvents();
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
    waitWhileBusy(tree, tree.getModel());
  }

  public static void waitWhileBusy(JTree tree, TreeModel model) {
    assertDispatchThreadWithoutWriteAccess();
    long startTimeMillis = System.currentTimeMillis();
    while (isBusy(tree, model)) {
      assertMaxWaitTimeSince(startTimeMillis);
      TimeoutUtil.sleep(5);
    }
  }

  public static void waitForCallback(@NotNull ActionCallback callback) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    callback.doWhenDone(() -> promise.setResult(null)).doWhenRejected((@NotNull Runnable)promise::cancel);
    waitForPromise(promise);
  }

  public static @Nullable <T> T waitForPromise(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME);
  }

  public static @Nullable <T> T waitForPromise(@NotNull Promise<T> promise, long timeout) {
    return waitForPromise(promise, timeout, false);
  }

  public static <T> T assertPromiseSucceeds(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME, true);
  }

  private static @Nullable <T> T waitForPromise(@NotNull Promise<T> promise, long timeout, boolean assertSucceeded) {
    assertDispatchThreadWithoutWriteAccess();
    long start = System.currentTimeMillis();
    while (true) {
      if (promise.getState() == Promise.State.PENDING) {
        UIUtil.dispatchAllInvocationEvents();
      }
      try {
        return promise.blockingGet(20, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (Exception e) {
        if (assertSucceeded) {
          throw new AssertionError(e);
        }
        else {
          return null;
        }
      }
      assertMaxWaitTimeSince(start, timeout);
    }
  }

  public static <T> T waitForFuture(@NotNull Future<T> future, long timeoutMillis) {
    assertDispatchThreadWithoutWriteAccess();
    long start = System.currentTimeMillis();
    while (true) {
      if (!future.isDone()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      try {
        return future.get(10, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (Exception e) {
        throw new AssertionError(e);
      }
      assertMaxWaitTimeSince(start, timeoutMillis);
    }
  }

  public static void waitForAlarm(final int delay) {
    @NotNull Application app = ApplicationManager.getApplication();
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
                                   "\n invocatorEdtQueue=" + LaterInvocator.getLaterInvocatorEdtQueue() +
                                   "\n invocatorWtQueue=" + LaterInvocator.getLaterInvocatorWtQueue()
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
  public static void dispatchAllEventsInIdeEventQueue() {
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    while (true) {
      try {
        if (dispatchNextEventIfAny(eventQueue) == null) break;
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
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

  public static StringBuilder print(AbstractTreeStructure structure, Object node, int currentLevel, @Nullable Comparator<?> comparator,
                                    int maxRowCount, char paddingChar, @Nullable Queryable.PrintInfo printInfo) {
    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, o -> toString(o, printInfo));
  }

  public static String print(AbstractTreeStructure structure, Object node, Function<Object, String> nodePresenter) {
    return print(structure, node, 0, Comparator.comparing(nodePresenter), -1, ' ', nodePresenter).toString();
  }

  private static StringBuilder print(AbstractTreeStructure structure, Object node, int currentLevel, @Nullable Comparator<?> comparator,
                                     int maxRowCount, char paddingChar, Function<Object, String> nodePresenter) {
    StringBuilder buffer = new StringBuilder();
    doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, 0, paddingChar, nodePresenter);
    return buffer;
  }

  private static int doPrint(StringBuilder buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             @Nullable Comparator<?> comparator,
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
      List<?> list = new ArrayList<>(Arrays.asList(children));
      @SuppressWarnings("unchecked")
      Comparator<Object> c = (Comparator<Object>)comparator;
      list.sort(c);
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

  public static String print(Collection<?> c) {
    return c.stream().map(each -> toString(each, null)).collect(Collectors.joining("\n"));
  }

  public static String print(@NotNull ListModel<?> model) {
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
   * An example: {@code startPerformanceTest("calculating pi",100, testRunnable).assertTiming();}
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public static PerformanceTestInfo startPerformanceTest(@NonNls @NotNull String what, int expectedMs, @NotNull ThrowableRunnable<?> test) {
    return new PerformanceTestInfo(test, expectedMs, what);
  }

  public static void assertPathsEqual(@Nullable String expected, @Nullable String actual) {
    if (expected != null) expected = FileUtil.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtil.toSystemIndependentName(actual);
    assertEquals(expected, actual);
  }

  public static @NotNull String getJavaExe() {
    return SystemProperties.getJavaHome() + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");
  }

  public static @NotNull URL getRtJarURL() {
    String home = SystemProperties.getJavaHome();
    try {
      return JavaVersion.current().feature >= 9 ? new URL("jrt:" + home) : new File(home + "/lib/rt.jar").toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void forceCloseProjectWithoutSaving(@NotNull Project project) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    });
  }

  public static void saveProject(@NotNull Project project) {
    saveProject(project, false);
  }

  public static void saveProject(@NotNull Project project, boolean isForceSavingAllSettings) {
    StoreReloadManager.getInstance().flushChangedProjectFileAlarm();
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

  @SuppressWarnings("CallToSystemGC")
  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      waitForAllBackgroundActivityToCalmDown();
      long duration = TimeoutUtil.measureExecutionTime(actionToMeasure::run);
      try {
        assertTiming(message, expected, duration);
        break;
      }
      catch (AssertionFailedError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
        String s = e.getMessage() + "\n  " + attempts + " " + StringUtil.pluralize("attempt", attempts) + " remain";
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
      File[] io = Objects.requireNonNull(new File(dir.getPath()).listFiles());
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
        throw new FileComparisonFailure("Text mismatch in the file " + fileExpected.getName(), expected, actual, fileExpected.getPath());
      }
    }
  }

  private static String fileText(VirtualFile file) throws IOException {
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    if (doc != null) {
      return doc.getText();
    }
    if (!file.getFileType().isBinary() || FileTypeRegistry.getInstance().isFileOfType(file, FileTypes.UNKNOWN)) {
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
      ApplicationManager.getApplication().runWriteAction(() -> {
        dirAfter.refresh(false, true);
        dirBefore.refresh(false, true);
      });
      assertDirectoriesEqual(dirAfter, dirBefore);
    }
    finally {
      FileUtilRt.delete(tempDir);
    }
  }

  public static @NotNull String getCommunityPath() {
    final String homePath = IdeaTestExecutionPolicy.getHomePathWithPolicy();
    if (new File(homePath, "community/.idea").isDirectory()) {
      return homePath + File.separatorChar + "community";
    }
    return homePath;
  }

  public static @NotNull String getPlatformTestDataPath() {
    return getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/";
  }

  @Contract(pure = true)
  public static @NotNull Comparator<AbstractTreeNode<?>> createComparator(final Queryable.PrintInfo printInfo) {
    return (o1, o2) -> {
      String displayText1 = o1.toTestString(printInfo);
      String displayText2 = o2.toTestString(printInfo);
      return Comparing.compare(displayText1, displayText2);
    };
  }

  public static @NotNull String loadFileText(@NotNull String fileName) throws IOException {
    return StringUtil.convertLineSeparators(FileUtil.loadFile(new File(fileName)));
  }

  public static void withEncoding(@NotNull String encoding, @NotNull ThrowableRunnable<?> r) {
    Charset.forName(encoding); // check the encoding exists
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

  @SuppressWarnings("ImplicitDefaultCharsetUsage")
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

  public static @NotNull List<WebReference> collectWebReferences(@NotNull PsiElement element) {
    List<WebReference> refs = new ArrayList<>();
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
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

  public static @NotNull List<UrlReference> collectUrlReferences(@NotNull PsiElement element) {
    List<UrlReference> result = new SmartList<>();
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        result.addAll(PsiSymbolReferenceService.getService().getReferences(element, UrlReference.class));
        super.visitElement(element);
      }
    });
    return result;
  }

  @SuppressWarnings("unchecked")
  public static @NotNull <T extends PsiReference> T getReferenceOfTypeWithAssertion(@Nullable PsiReference reference, Class<T> refType) {
    if (refType.isInstance(reference)) return (T)reference;
    if (reference instanceof PsiMultiReference) {
      PsiReference[] psiReferences = ((PsiMultiReference)reference).getReferences();
      for (PsiReference psiReference : psiReferences) {
        if (refType.isInstance(psiReference)) return (T)psiReference;
      }
    }
    throw new AssertionError(
      "given reference should be " + refType + " but " + (reference != null ? reference.getClass() : null) + " was given");
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

  public static void captureMemorySnapshot() {
    try {
      String className = "com.jetbrains.performancePlugin.profilers.YourKitProfilerHandler";
      Method snapshot = ReflectionUtil.getMethod(Class.forName(className), "captureMemorySnapshot");
      if (snapshot != null) {
        Object path = snapshot.invoke(null);
        System.out.println("Memory snapshot captured to '" + path + "'");
      }
    }
    catch (ClassNotFoundException e) {
      // YourKitProfilerHandler is missing from the classpath, ignore
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

  /*
   * 1. Think twice before use - do you really need to use VFS.
   * 2. Be aware the method doesn't refresh VFS as it should be done in tests (see {@link PlatformTestCase#synchronizeTempDirVfs})
   *    (it is assumed that project is already created in a correct way).
   */
  public static @NotNull VirtualFile getOrCreateProjectBaseDir(@NotNull Project project) {
    return HeavyTestHelper.getOrCreateProjectBaseDir(project);
  }

  public static @Nullable RunConfiguration getRunConfiguration(@NotNull PsiElement element, @NotNull RunConfigurationProducer<?> producer) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, element.getProject());
    dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element));
    final Location<PsiElement> location = PsiLocation.fromPsiElement(element);
    dataContext.put(Location.DATA_KEY, location);

    ConfigurationContext cc = ConfigurationContext.getFromContext(dataContext);

    final ConfigurationFromContext configuration = producer.createConfigurationFromContext(cc);
    return configuration != null ? configuration.getConfiguration() : null;
  }

  public static ExecutionEnvironment executeConfiguration(@NotNull RunConfiguration runConfiguration) throws InterruptedException {
    return executeConfiguration(runConfiguration, DefaultRunExecutor.EXECUTOR_ID);
  }

  public static ExecutionEnvironment executeConfiguration(@NotNull RunConfiguration runConfiguration, @NotNull String executorId) throws InterruptedException {
    Project project = runConfiguration.getProject();
    ConfigurationFactory factory = runConfiguration.getFactory();
    if (factory == null) {
      fail("No factory found for: " + runConfiguration);
    }
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
      RunManager.getInstance(project).createConfiguration(runConfiguration, factory);
    ProgramRunner<?> runner = ProgramRunner.getRunner(executorId, runConfiguration);
    if (runner == null) {
      fail("No runner found for: " + executorId + " and " + runConfiguration);
    }
    Ref<RunContentDescriptor> refRunContentDescriptor = new Ref<>();
    ExecutionEnvironment executionEnvironment =
      new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), runner, runnerAndConfigurationSettings,
                               project);
    CountDownLatch latch = new CountDownLatch(1);
    ProgramRunnerUtil.executeConfigurationAsync(executionEnvironment, false, false, new ProgramRunner.Callback() {
      @Override
      public void processStarted(RunContentDescriptor descriptor) {
        LOG.debug("Process started");
        refRunContentDescriptor.set(descriptor);
        latch.countDown();
      }
    });
    latch.await(60, TimeUnit.SECONDS);
    ProcessHandler processHandler = refRunContentDescriptor.get().getProcessHandler();
    if (processHandler == null) {
      fail("No process handler found");
    }

    CapturingProcessAdapter capturingProcessAdapter = new CapturingProcessAdapter();
    processHandler.addProcessListener(capturingProcessAdapter);
    processHandler.waitFor(60000);

    LOG.debug("Process terminated: " + processHandler.isProcessTerminated());
    ProcessOutput processOutput = capturingProcessAdapter.getOutput();
    LOG.debug("Exit code: " + processOutput.getExitCode());
    LOG.debug("Stdout: " + processOutput.getStdout());
    LOG.debug("Stderr: " + processOutput.getStderr());

    return executionEnvironment;
  }

  public static PsiElement findElementBySignature(@NotNull String signature, @NotNull String fileRelativePath, @NotNull Project project) {
    String filePath = project.getBasePath() + File.separator + fileRelativePath;
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (virtualFile == null || !virtualFile.exists()) {
      throw new IllegalArgumentException(String.format("File '%s' doesn't exist", filePath));
    }
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }
    int offset = psiFile.getText().indexOf(signature);
    return psiFile.findElementAt(offset);
  }

  public static void useAppConfigDir(ThrowableRunnable<? extends Exception> task) throws Exception {
    Path configDir = PathManager.getConfigDir();
    Path configCopy;
    if (Files.exists(configDir)) {
      configCopy = Files.move(configDir, Paths.get(configDir + "_bak"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    else {
      FileUtil.delete(configDir);
      configCopy = null;
    }

    try {
      task.run();
    }
    finally {
      FileUtil.delete(configDir);
      if (configCopy != null) {
        Files.move(configCopy, configDir, StandardCopyOption.ATOMIC_MOVE);
      }
    }
  }

  public static @NotNull Project loadAndOpenProject(@NotNull Path path) {
    return Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(path, new OpenProjectTaskBuilder().build()));
  }

  public static void openProject(@NotNull Project project) {
    if (!ProjectManagerEx.getInstanceEx().openProject(project)) {
      throw new IllegalStateException("openProject returned false");
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      dispatchAllInvocationEventsInIdeEventQueue();
    }
  }
}