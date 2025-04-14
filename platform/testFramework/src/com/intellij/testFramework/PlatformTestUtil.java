// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.CoroutineDumperKt;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.UrlReference;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.testFramework.common.TestApplicationKt;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.junit.AssumptionViolatedException;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.splitByLines;
import static com.intellij.testFramework.UsefulTestCase.assertSameLines;
import static com.intellij.util.containers.ContainerUtil.sorted;
import static org.junit.Assert.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
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
   * @deprecated moved to {@link TestApplicationKt#loadApp(Runnable)}
   */
  @Deprecated
  static void loadApp(@NotNull Runnable setupEventQueue) {
    TestApplicationKt.loadApp(setupEventQueue);
  }

  /**
   * @see ExtensionPointImpl#maskAll(List, Disposable, boolean)
   */
  public static <T> void maskExtensions(@NotNull ProjectExtensionPointName<T> pointName,
                                        @NotNull Project project,
                                        @NotNull List<? extends T> newExtensions,
                                        @NotNull Disposable parentDisposable) {
    ((ExtensionPointImpl<@NotNull T>)pointName.getPoint(project)).maskAll(newExtensions, parentDisposable, true);
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

  public static @NotNull String print(@NotNull JTree tree, boolean withSelection) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, null);
  }

  public static @NotNull String print(@NotNull JTree tree, @NotNull TreePath path, @Nullable Queryable.PrintInfo printInfo, boolean withSelection) {
    return print(tree, path,  withSelection, printInfo, null);
  }

  public static @NotNull String print(@NotNull JTree tree, boolean withSelection, @Nullable Predicate<? super String> nodePrintCondition) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, nodePrintCondition);
  }

  private static String print(JTree tree,
                              TreePath path,
                              boolean withSelection,
                              @Nullable Queryable.PrintInfo printInfo,
                              @Nullable Predicate<? super String> nodePrintCondition) {
    Collection<String> strings = new ArrayList<>();
    printImpl(tree, path, strings, 0, withSelection, printInfo, nodePrintCondition);
    return String.join("\n", strings);
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
      buff.append(expanded ? '-' : '+');
    }

    boolean selected = tree.getSelectionModel().isPathSelected(path);
    if (withSelection && selected) {
      buff.append('[');
    }

    buff.append(nodeText);

    if (withSelection && selected) {
      buff.append(']');
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
      List<String> actualLines = sorted(ContainerUtil.map(splitByLines(treeStringPresentation), String::trim));
      List<String> expectedLines = sorted(ContainerUtil.map(splitByLines(expected), String::trim));
      assertEquals("Expected:\n" + expected + "\nActual:\n" + treeStringPresentation, expectedLines, actualLines);
    }
    else {
      assertSameLines(expected.trim(), treeStringPresentation.trim());
    }
  }

  public static void expand(@NotNull JTree tree, int @NotNull ... rows) {
    for (int row : rows) {
      tree.expandRow(row);
      waitWhileBusy(tree);
    }
  }

  public static void expandAll(@NotNull JTree tree) {
    expandAll(tree, path -> !(TreeUtil.getLastUserObject(path) instanceof ExternalLibrariesNode));
  }

  public static void expandAll(@NotNull JTree tree, @NotNull Predicate<@NotNull TreePath> predicate) {
    // Ignore AbstractTreeNode.isIncludedInExpandAll because some tests need to expand
    // more than that, but not the External Libraries node which is huge and only wastes time.
    waitForPromise(TreeUtil.promiseExpand(
      tree,
      Integer.MAX_VALUE,
      predicate));
  }

  private static long getMillisSince(long startTimeMillis) {
    return System.currentTimeMillis() - startTimeMillis;
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis) {
    assertMaxWaitTimeSince(startTimeMillis, MAX_WAIT_TIME);
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis, long timeoutMillis) {
    long took = getMillisSince(startTimeMillis);
    if (took <= timeoutMillis) {
      return;
    }

    throw new AssertionError(
      "The waiting takes too long. " +
      "Expected to take no more than: " + timeoutMillis + " ms but took: " + took + " ms\n" +
      "Thread dump: " + ThreadDumper.dumpThreadsToString() + "\n" +
      "Coroutine dump: " + CoroutineDumperKt.dumpCoroutines(null, true, true) + "\n"
    );
  }

  private static void assertDispatchThreadWithoutWriteAccess() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      // skipping write access check in simple tests
      assertEventQueueDispatchThread();
    }
    else {
      assert !application.isWriteAccessAllowed() : "do not wait under write action to avoid possible deadlock";
      ThreadingAssertions.assertEventDispatchThread();
    }
  }

  private static void assertEventQueueDispatchThread() {
    if (!EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Must be called from EDT but got: " + Thread.currentThread());
    }
  }

  private static boolean isBusy(@NotNull JTree tree, TreeModel model) {
    UIUtil.dispatchAllInvocationEvents();
    if (ClientProperty.isTrue(tree, TreeUtil.TREE_IS_BUSY)) return true;
    if (model instanceof AsyncTreeModel async) {
      if (async.isProcessing()) return true;
      UIUtil.dispatchAllInvocationEvents();
      return async.isProcessing();
    }
    return false;
  }

  public static void waitWhileBusy(@NotNull JTree tree) {
    waitWhileBusy(() -> isBusy(tree, tree.getModel()));
  }

  public static void waitWhileBusy(@NotNull Supplier<Boolean> busyCondition) {
    assertDispatchThreadWithoutWriteAccess();
    long startTimeMillis = System.currentTimeMillis();
    while (busyCondition.get()) {
      assertMaxWaitTimeSince(startTimeMillis);
      TimeoutUtil.sleep(5);
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  public static void waitForCallback(@NotNull ActionCallback callback) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    callback.doWhenDone(() -> promise.setResult(null)).doWhenRejected((@NotNull Runnable)promise::cancel);
    waitForPromise(promise);
  }

  public static <T> @Nullable T waitForPromise(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME, false);
  }

  public static <T> @Nullable T waitForPromise(@NotNull Promise<T> promise, long timeoutMillis) {
    return waitForPromise(promise, timeoutMillis, false);
  }

  public static <T> @Nullable T assertPromiseSucceeds(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME, true);
  }

  private static @Nullable <T> T waitForPromise(@NotNull Promise<T> promise, long timeoutMillis, boolean assertSucceeded) {
    assertDispatchThreadWithoutWriteAccess();
    long start = System.currentTimeMillis();
    while (true) {
      if (promise.getState() == Promise.State.PENDING) {
        UIUtil.dispatchAllInvocationEvents();
      }
      try {
        return promise.blockingGet(20, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) { }
      catch (Exception e) {
        if (assertSucceeded) {
          throw new AssertionError(e);
        }
        else {
          return null;
        }
      }
      assertMaxWaitTimeSince(start, timeoutMillis);
    }
  }

  public static <T> T waitForFuture(@NotNull Future<T> future) {
    return waitForFuture(future, MAX_WAIT_TIME);
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

  public static void waitForAlarm(int delay) {
    @NotNull Application app = ApplicationManager.getApplication();
    assertDispatchThreadWithoutWriteAccess();

    Disposable tempDisposable = Disposer.newDisposable();

    AtomicBoolean runnableInvoked = new AtomicBoolean();
    AtomicBoolean pooledRunnableInvoked = new AtomicBoolean();
    AtomicBoolean alarmInvoked1 = new AtomicBoolean();
    AtomicBoolean alarmInvoked2 = new AtomicBoolean();
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, tempDisposable);
    Alarm pooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, tempDisposable);
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
                                   "; non-modal=" + (initialModality == ModalityState.nonModal()) +
                                   "; invokeLater passed=" + runnableInvoked.get() +
                                   "; pooled alarm passed=" + pooledRunnableInvoked.get() +
                                   "; app.disposed=" + app.isDisposed() +
                                   "; alarm.disposed=" + alarm.isDisposed() +
                                   "; alarm.requests=" + alarm.getActiveRequestCount() +
                                   "\n delayQueue=" + StringUtil.trimLog(queue, 1000) +
                                   "\n invocatorEdtQueue=" + LaterInvocator.getLaterInvocatorEdtQueue()
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
    assertDispatchThreadWithoutWriteAccess();
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      while (true) {
        AWTEvent event = eventQueue.peekEvent();
        if (event == null) break;
        event = eventQueue.getNextEvent();
        if (event instanceof InvocationEvent) {
          eventQueue.dispatchEvent(event);
        }
      }
    }
  }

  /**
   * Dispatch all pending events (if any) in the {@link IdeEventQueue}. Should only be invoked from EDT.
   */
  public static void dispatchAllEventsInIdeEventQueue() {
    assertEventQueueDispatchThread();
    while (true) {
      try {
        if (dispatchNextEventIfAny() == null) break;
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Dispatch one pending event (if any) in the {@link IdeEventQueue}. Should only be invoked from EDT.
   */
  public static AWTEvent dispatchNextEventIfAny() throws InterruptedException {
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      assertEventQueueDispatchThread();
      IdeEventQueue eventQueue = IdeEventQueue.getInstance();
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) return null;
      AWTEvent event1 = eventQueue.getNextEvent();
      eventQueue.dispatchEvent(event1);
      return event1;
    }
  }

  public static @NotNull StringBuilder print(@NotNull AbstractTreeStructure structure,
                                             @NotNull Object node,
                                             int currentLevel,
                                             @Nullable Comparator<?> comparator,
                                             int maxRowCount,
                                             char paddingChar,
                                             @Nullable Queryable.PrintInfo printInfo) {
    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, o -> toString(o, printInfo));
  }

  public static @NotNull String print(@NotNull AbstractTreeStructure structure,
                                      @NotNull Object node,
                                      @NotNull Function<Object, String> nodePresenter) {
    return print(structure, node, 0, Comparator.comparing(nodePresenter), -1, ' ', nodePresenter).toString();
  }

  private static @NotNull StringBuilder print(AbstractTreeStructure structure,
                                              Object node,
                                              int currentLevel,
                                              @Nullable Comparator<?> comparator,
                                              int maxRowCount,
                                              char paddingChar,
                                              Function<Object, String> nodePresenter) {
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

  public static @NotNull String print(Object @NotNull [] objects) {
    return print(Arrays.asList(objects));
  }

  public static @NotNull String print(@NotNull Collection<?> c) {
    return c.stream().map(each -> toString(each, null)).collect(Collectors.joining("\n"));
  }

  public static @NotNull String print(@NotNull ListModel<?> model) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < model.getSize(); i++) {
      result.append(toString(model.getElementAt(i), null));
      result.append("\n");
    }
    return result.toString();
  }

  public static @NotNull String print(@NotNull JTree tree) {
    return print(tree, false);
  }

  /**
   * @see IdeActions
   */
  public static void invokeNamedAction(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    assertNotNull(action);
    @SuppressWarnings("deprecation") DataContext context = DataManager.getInstance().getDataContext();
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, event);
    ActionUtil.performDumbAwareUpdate(action, event, false);
    assertTrue(event.getPresentation().isEnabled());
    ActionUtil.performActionDumbAwareWithCallbacks(action, event);
  }

  public static void assertTiming(@NotNull String message, long expectedMillis, long actualMillis) {
    if (COVERAGE_ENABLED_BUILD) return;

    long expectedOnMyMachine = Math.max(1, expectedMillis * Timings.CPU_TIMING / Timings.REFERENCE_CPU_TIMING);

    // Allow 10% more in case of test machine is busy.
    String logMessage = message;
    if (actualMillis > expectedOnMyMachine) {
      int percentage = (int)(100.0 * (actualMillis - expectedOnMyMachine) / expectedOnMyMachine);
      logMessage += ". Operation took " + percentage + "% longer than expected";
    }
    logMessage += ". Expected on my machine: " + expectedOnMyMachine + "." +
                  " Actual: " + actualMillis + "." +
                  " Expected on Standard machine: " + expectedMillis + ";" +
                  " Timings: CPU=" + Timings.CPU_TIMING +
                  ", I/O=" + Timings.IO_TIMING + ".";
    double acceptableChangeFactor = 1.1;
    if (actualMillis < expectedOnMyMachine) {
      System.out.println(logMessage);
      TeamCityLogger.info(logMessage);
    }
    else if (actualMillis < expectedOnMyMachine * acceptableChangeFactor) {
      TeamCityLogger.warning(logMessage, null);
    }
    else {
      // throw AssertionFailedError to try one more time
      throw new AssertionFailedError(logMessage);
    }
  }

  /**
   * Init a performance test.<br/>
   * E.g: {@code newBenchmark("calculating pi", () -> { CODE_TO_BE_MEASURED_IS_HERE }).start();}
   * If you need to customize published metrics, use
   * {@code com.intellij.tools.ide.metrics.benchmark.Benchmark#newBenchmark} and
   * method {@code PerformanceTestInfoImpl#withMetricsCollector}.
   * @see BenchmarkTestInfo#start()
   */
  // to warn about not calling .assertTiming() in the end
  @Contract(pure = true)
  public static @NotNull BenchmarkTestInfo newBenchmark(@NonNls @NotNull String launchName, @NotNull ThrowableRunnable<?> test) {
    return newBenchmarkWithVariableInputSize(launchName, 1, () -> {
      test.run();
      return 1;
    });
  }

  /**
   * Init a performance test which input may change.<br/>
   * E.g: it depends on the number of files in the project.
   * <p>
   * @param expectedInputSize specifies size of the input,
   * @param test returns actual size of the input. It is supposed that the execution time is lineally proportionally dependent on the input size.
   *
   * @see BenchmarkTestInfo#start()
   * </p>
   */
  @Contract(pure = true)
  public static @NotNull BenchmarkTestInfo newBenchmarkWithVariableInputSize(@NonNls @NotNull String launchName,
                                                                             int expectedInputSize,
                                                                             @NotNull ThrowableComputable<Integer, ?> test) {
    return BenchmarkTestInfoLoader.Companion.getInstance().initialize(test, expectedInputSize, launchName);
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
      return CurrentJavaVersion.currentJavaVersion().feature >= 9 ? new URL("jrt:" + home) : new File(home + "/lib/rt.jar").toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void forceCloseProjectWithoutSaving(@NotNull Project project) {
    if (!project.isDisposed()) {
      ApplicationManager.getApplication().invokeAndWait(() -> ProjectManagerEx.getInstanceEx().forceCloseProject(project));
    }
  }

  public static void saveProject(@NotNull Project project) {
    OpenProjectTaskBuilderKt.saveProject(project, false);
  }

  public static void saveProject(@NotNull Project project, boolean isForceSavingAllSettings) {
    OpenProjectTaskBuilderKt.saveProject(project, isForceSavingAllSettings);
  }

  public static void waitForAllBackgroundActivityToCalmDown() {
    for (int i = 0; i < 50; i++) {
      CpuUsageData data = CpuUsageData.measureCpuUsage(() -> TimeoutUtil.sleep(100));
      if (!data.hasAnyActivityBesides(Thread.currentThread())) {
        break;
      }
    }
  }

  public static void assertTiming(@NotNull String message, long expectedMillis, @NotNull Runnable actionToMeasure) {
    assertTiming(message, expectedMillis, 4, actionToMeasure);
  }

  @SuppressWarnings("CallToSystemGC")
  public static void assertTiming(@NotNull String message, long expectedMillis, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      waitForAllBackgroundActivityToCalmDown();
      long duration = TimeoutUtil.measureExecutionTime(actionToMeasure::run);
      try {
        assertTiming(message, expectedMillis, duration);
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

  private static @NotNull Map<String, VirtualFile> buildNameToFileMap(VirtualFile @NotNull [] files,
                                                                      @Nullable VirtualFileFilter filter,
                                                                      @Nullable Function<VirtualFile, String> fileNameMapper) {
    Map<String, VirtualFile> map = new HashMap<>();
    for (VirtualFile file : files) {
      if (filter != null && !filter.accept(file)) continue;
      String fileName = fileNameMapper != null ? fileNameMapper.apply(file) : file.getName();
      map.put(fileName, file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(@NotNull VirtualFile dirExpected, @NotNull VirtualFile dirActual) throws IOException {
    assertDirectoriesEqual(dirExpected, dirActual, null);
  }

  public static void assertDirectoriesEqual(@NotNull VirtualFile dirExpected,
                                            @NotNull VirtualFile dirActual,
                                            @Nullable VirtualFileFilter fileFilter) throws IOException {
    assertDirectoriesEqual(dirExpected, dirActual, fileFilter, null);
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  public static void assertDirectoriesEqual(@NotNull VirtualFile dirExpected,
                                            @NotNull VirtualFile dirActual,
                                            @Nullable VirtualFileFilter fileFilter,
                                            @Nullable Function<VirtualFile, String> fileNameMapper) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile[] childrenAfter = dirExpected.getChildren();
    shallowCompare(dirExpected, childrenAfter);

    VirtualFile[] childrenBefore = dirActual.getChildren();
    shallowCompare(dirActual, childrenBefore);

    Map<String, VirtualFile> mapAfter = buildNameToFileMap(childrenAfter, fileFilter, fileNameMapper);
    Map<String, VirtualFile> mapBefore = buildNameToFileMap(childrenBefore, fileFilter, fileNameMapper);

    Set<String> keySetAfter = mapAfter.keySet();
    Set<String> keySetBefore = mapBefore.keySet();
    assertEquals(dirExpected.getPath(), keySetAfter, keySetBefore);

    for (String name : keySetAfter) {
      VirtualFile fileAfter = mapAfter.get(name);
      VirtualFile fileBefore = mapBefore.get(name);
      if (fileAfter.isDirectory()) {
        assertDirectoriesEqual(fileAfter, fileBefore, fileFilter, fileNameMapper);
      }
      else {
        assertFilesEqual(fileAfter, fileBefore);
      }
    }
  }

  private static void shallowCompare(@NotNull VirtualFile dir, VirtualFile @NotNull [] vfs) {
    if (dir.isInLocalFileSystem() && dir.getFileSystem() != TempFileSystem.getInstance()) {
      String vfsPaths = Stream.of(vfs).map(VirtualFile::getPath).sorted().collect(Collectors.joining("\n"));
      File[] io = Objects.requireNonNull(new File(dir.getPath()).listFiles());
      String ioPaths = Stream.of(io).map(f -> FileUtil.toSystemIndependentName(f.getPath())).sorted().collect(Collectors.joining("\n"));
      assertEquals(vfsPaths, ioPaths);
    }
  }

  public static void assertFilesEqual(@NotNull VirtualFile fileExpected, @NotNull VirtualFile fileActual) throws IOException {
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
        throw new FileComparisonFailedError("Text mismatch in the file " + fileExpected.getName(), expected, actual,
                                            fileActual.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH));
      }
    }
  }

  private static String fileText(@NotNull VirtualFile file) throws IOException {
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
    Path tempDir = Files.createTempDirectory("assert_jar_tmp_");
    try (JarFile jarFile1 = new JarFile(file1); JarFile jarFile2 = new JarFile(file2)) {
      Path tempDirectory1 = Files.createDirectory(tempDir.resolve("tmp1"));
      Path tempDirectory2 = Files.createDirectory(tempDir.resolve("tmp2"));

      new Decompressor.Zip(new File(jarFile1.getName())).extract(tempDirectory1);
      new Decompressor.Zip(new File(jarFile2.getName())).extract(tempDirectory2);

      VirtualFile dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDirectory1);
      assertNotNull(tempDirectory1.toString(), dirAfter);
      VirtualFile dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDirectory2);
      assertNotNull(tempDirectory2.toString(), dirBefore);
      ApplicationManager.getApplication().runWriteAction(() -> {
        dirAfter.refresh(false, true);
        dirBefore.refresh(false, true);
      });
      assertDirectoriesEqual(dirAfter, dirBefore);
    }
    finally {
      NioFiles.deleteRecursively(tempDir);
    }
  }

  public static @NotNull String getCommunityPath() {
    String homePath = IdeaTestExecutionPolicy.getHomePathWithPolicy();
    if (new File(homePath, "community/.idea").isDirectory()) {
      homePath = homePath + File.separatorChar + "community";
    }
    return homePath;
  }

  public static @NotNull String getPlatformTestDataPath() {
    return getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/";
  }

  @Contract(pure = true)
  public static @NotNull Comparator<AbstractTreeNode<?>> createComparator(Queryable.PrintInfo printInfo) {
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

  private static void patchSystemFileEncoding(@NotNull String encoding) {
    ReflectionUtil.resetField(Charset.class, Charset.class, "defaultCharset");
    System.setProperty("file.encoding", encoding);
  }

  @SuppressWarnings("ImplicitDefaultCharsetUsage")
  public static void withStdErrSuppressed(@NotNull Runnable r) {
    PrintStream std = System.err;
    System.setErr(new PrintStream(OutputStream.nullOutputStream()));
    try {
      r.run();
    }
    finally {
      System.setErr(std);
    }
  }

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

  public static @NotNull <T extends PsiReference> T getReferenceOfTypeWithAssertion(@Nullable PsiReference reference, @NotNull Class<T> refType) {
    if (refType.isInstance(reference)) {
      //noinspection unchecked
      return (T)reference;
    }
    if (reference instanceof PsiMultiReference) {
      PsiReference[] psiReferences = ((PsiMultiReference)reference).getReferences();
      for (PsiReference psiReference : psiReferences) {
        if (refType.isInstance(psiReference)) {
          //noinspection unchecked
          return (T)psiReference;
        }
      }
    }
    throw new AssertionError("given reference should be " + refType + " but " + (reference != null ? reference.getClass() : null) + " was given");
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

  public static <T> void assertComparisonContractNotViolated(@NotNull List<? extends T> values,
                                                             @NotNull Comparator<? super T> comparator,
                                                             @NotNull BiPredicate<? super T, ? super T> equality) {
    for (int i1 = 0; i1 < values.size(); i1++) {
      for (int i2 = i1; i2 < values.size(); i2++) {
        T value1 = values.get(i1);
        T value2 = values.get(i2);

        int result12 = comparator.compare(value1, value2);
        int result21 = comparator.compare(value2, value1);
        if (equality.test(value1, value2)) {
          if (result12 != 0) fail(String.format("Equal, but not 0: '%s' - '%s'", value1, value2));
          if (result21 != 0) fail(String.format("Equal, but not 0: '%s' - '%s'", value2, value1));
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
                                                                        """
                                                                          /**
                                                                           * Created by ${USER} on ${DATE}.
                                                                           */
                                                                          """, parentDisposable);
  }

  /**
   * 1. Think twice before use - do you really need to use VFS?
   * 2. Be aware the method doesn't refresh VFS as it should be done in tests (see {@link HeavyPlatformTestCase#synchronizeTempDirVfs})
   *    (it is assumed that the project is already created in a correct way).
   */
  public static @NotNull VirtualFile getOrCreateProjectBaseDir(@NotNull Project project) {
    return HeavyTestHelper.getOrCreateProjectBaseDir(project);
  }

  public static @Nullable RunConfiguration getRunConfiguration(@NotNull PsiElement element, @NotNull RunConfigurationProducer<?> producer) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, element.getProject());
    dataContext.put(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element));
    Location<PsiElement> location = PsiLocation.fromPsiElement(element);
    dataContext.put(Location.DATA_KEY, location);

    ConfigurationContext cc = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    ConfigurationFromContext configuration = producer.createConfigurationFromContext(cc);
    return configuration != null ? configuration.getConfiguration() : null;
  }

  /**
   * Executes {@code runConfiguration} with {@link DefaultRunExecutor#EXECUTOR_ID run} executor,
   * then waits for 60 seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(@NotNull RunConfiguration runConfiguration) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, DefaultRunExecutor.EXECUTOR_ID);
  }

  /**
   * Executes {@code runConfiguration} with {@link DefaultRunExecutor#EXECUTOR_ID run} executor,
   * then waits for {@code timeoutInSeconds} seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(@NotNull RunConfiguration runConfiguration,
                                                                          long timeoutInSeconds) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, DefaultRunExecutor.EXECUTOR_ID, timeoutInSeconds);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId}, then waits for 60 seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(@NotNull RunConfiguration runConfiguration,
                                                                          @NotNull String executorId) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, executorId, 60);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId},
   * then waits for the {@code timeoutInSeconds} seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(@NotNull RunConfiguration runConfiguration,
                                                                          @NotNull String executorId,
                                                                          long timeoutInSeconds) throws InterruptedException {
    Pair<@NotNull ExecutionEnvironment, RunContentDescriptor> result = executeConfiguration(runConfiguration, executorId, null);
    ProcessHandler processHandler = result.second.getProcessHandler();
    assertNotNull("Process handler must not be null!", processHandler);
    waitWithEventsDispatching(
      () -> "Process failed to finish in " + timeoutInSeconds + " seconds: " + processHandler,
      processHandler::isProcessTerminated, Math.toIntExact(timeoutInSeconds),
      () -> {
        if (!processHandler.isProcessTerminated()) {
          LOG.debug("Destroying process: " + processHandler);
          processHandler.destroyProcess();
        }
      });

    return result.first;
  }

  /**
   * @see PlatformTestUtil#executeConfiguration(RunConfiguration, Executor, Consumer)
   */
  public static @NotNull Pair<@NotNull ExecutionEnvironment, RunContentDescriptor> executeConfiguration(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId,
    @Nullable Consumer<? super RunContentDescriptor> contentDescriptorProcessor)
    throws InterruptedException {
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    assertNotNull("Unable to find executor: " + executorId, executor);
    return executeConfiguration(runConfiguration, executor, contentDescriptorProcessor);
  }

  /**
   * Executes {@code runConfiguration} with executor defined by {@code executorId} and returns a pair of {@link ExecutionEnvironment} and
   * {@link RunContentDescriptor}.
   *
   * @param descriptorProcessor optional processor for the run content descriptor of executed configuration
   */
  public static @NotNull Pair<@NotNull ExecutionEnvironment, RunContentDescriptor> executeConfiguration(
    @NotNull RunConfiguration runConfiguration,
    @NotNull Executor executor,
    @Nullable Consumer<? super RunContentDescriptor> descriptorProcessor
  ) throws InterruptedException {
    Project project = runConfiguration.getProject();
    ConfigurationFactory factory = runConfiguration.getFactory();
    if (factory == null) {
      fail("No factory found for: " + runConfiguration);
    }
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
      RunManager.getInstance(project).createConfiguration(runConfiguration, factory);
    ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), runConfiguration);
    if (runner == null) {
      fail("No runner found for: " + executor.getId() + " and " + runConfiguration);
    }
    Ref<RunContentDescriptor> refRunContentDescriptor = new Ref<>();
    ExecutionEnvironment executionEnvironment = new ExecutionEnvironment(executor, runner, runnerAndConfigurationSettings, project);
    boolean[] failure = {false};
    ProgramRunnerUtil.executeConfigurationAsync(executionEnvironment, false, false, new ProgramRunner.Callback() {
      @Override
      public void processNotStarted(@Nullable Throwable error) {
        failure[0] = true;
      }

      @Override
      public void processStarted(RunContentDescriptor descriptor) {
        ProcessHandler processHandler = descriptor.getProcessHandler();
        LOG.debug("Process started: ", processHandler);
        if (descriptorProcessor != null) {
          descriptorProcessor.accept(descriptor);
        }
        assertNotNull(processHandler);
        processHandler.addProcessListener(new ProcessListener() {
          @Override
          public void startNotified(@NotNull ProcessEvent event) {
            LOG.debug("Process notified: ", processHandler);
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            LOG.debug("Process terminated: exitCode: ", event.getExitCode(), "; text: ", event.getText(), "; process: ", processHandler);
          }

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            LOG.debug(outputType + ": " + event.getText());
          }
        });
        refRunContentDescriptor.set(descriptor);
      }
    });
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    waitWithEventsDispatching("Process failed to start in 60 seconds", () -> !refRunContentDescriptor.isNull() || failure[0], 60);
    assertFalse("Process could not start for configuration: " + runConfiguration, failure[0]);
    return Pair.create(executionEnvironment, refRunContentDescriptor.get());
  }

  /**
   * Invokes {@code action} on bgt, waiting it to complete for {@code timeoutSeconds seconds} and return a result. Dispatches events while
   * waiting bgt to finish, so it is safe to invoke edt stuff if necessary. Be careful using from under lock, because it may cause a deadlock.
   */
  @RequiresEdt
  public static @Nullable <T> T callOnBgtSynchronously(@NotNull Callable<T> action, int timeoutSeconds) {
    var future = ApplicationManager.getApplication().executeOnPooledThread(action);
    waitWithEventsDispatching("Could not finish the call in " + timeoutSeconds + " seconds", future::isDone, timeoutSeconds);
    try {
      return future.get();
    }
    catch (InterruptedException | java.util.concurrent.ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public static void waitWithEventsDispatching(@NotNull String errorMessage, @NotNull BooleanSupplier condition, int timeoutInSeconds) {
    waitWithEventsDispatching(() -> errorMessage, condition, timeoutInSeconds);
  }

  public static void waitWithEventsDispatching(@NotNull Supplier<String> errorMessageSupplier,
                                               @NotNull BooleanSupplier condition,
                                               int timeoutInSeconds) {
    waitWithEventsDispatching(errorMessageSupplier, condition, timeoutInSeconds, null);
  }

  /**
   * Wait and dispatch events during timeout.
   * A {@link Runnable} callback may be provided to be executed when {@code condition} gets satisfied or {@code timeoutInSeconds} runs out.
   */
  public static void waitWithEventsDispatching(@NotNull Supplier<String> errorMessageSupplier,
                                               @NotNull BooleanSupplier condition,
                                               int timeoutInSeconds,
                                               @Nullable Runnable callback) {
    long start = System.currentTimeMillis();
    while (true) {
      try {
        if (System.currentTimeMillis() - start > timeoutInSeconds * 1000L) {
          if (callback != null) {
            callback.run();
          }
          fail(errorMessageSupplier.get());
        }
        if (condition.getAsBoolean()) {
          if (callback != null) {
            callback.run();
          }
          break;
        }
        dispatchAllEventsInIdeEventQueue();
        //noinspection BusyWait
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
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

  public static void useAppConfigDir(@NotNull ThrowableRunnable<? extends Exception> task) throws Exception {
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

  public static @NotNull Project loadAndOpenProject(@NotNull Path path, @NotNull Disposable parent) {
    Project project = Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(path, new OpenProjectTaskBuilder().build()));
    Disposer.register(parent, () -> forceCloseProjectWithoutSaving(project));
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    return project;
  }

  @SuppressWarnings("deprecation")
  public static boolean isUnderCommunityClassPath() {
    // StdFileTypes.JSPX is assigned to PLAIN_TEXT in IDEA Community
    return StdFileTypes.JSPX == FileTypes.PLAIN_TEXT;
  }

  public static <E extends Throwable> void withSystemProperty(@NotNull String key, String value, @NotNull ThrowableRunnable<E> task) throws E {
    String original = System.setProperty(key, value);
    try {
      task.run();
    }
    finally {
      SystemProperties.setProperty(key, original);
    }
  }

  /**
   * throws if the CPU cores number is too low for parallel tests
   */
  public static void assumeEnoughParallelism() throws AssumptionViolatedException {
    int N = Math.min(Runtime.getRuntime().availableProcessors(), Math.min(ForkJoinPool.getCommonPoolParallelism(), ForkJoinPool.commonPool().getParallelism()));
    if (N < 4) {
      throw new AssumptionViolatedException(
        "not enough parallelism, couldn't test parallel performance: " +
        "available CPU cores=" + Runtime.getRuntime().availableProcessors() +
        "; FJP configured parallelism=" + ForkJoinPool.getCommonPoolParallelism() +
        "; FJP actual common pool parallelism=" + ForkJoinPool.commonPool().getParallelism());
    }
  }
}
