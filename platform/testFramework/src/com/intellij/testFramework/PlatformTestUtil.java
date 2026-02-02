// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.CoroutineDumperKt;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.application.impl.TestOnlyThreading;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.UrlReference;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
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
import com.intellij.util.system.OS;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;
import org.junit.AssumptionViolatedException;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.splitByLines;
import static com.intellij.testFramework.UsefulTestCase.assertSameLines;
import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "UIUtilDispatchAllInvocationEventsInTests"})
public final class PlatformTestUtil {
  private static final Logger LOG = Logger.getInstance(PlatformTestUtil.class);

  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));

  private static final List<Runnable> ourProjectCleanups = new CopyOnWriteArrayList<>();
  private static final long MAX_WAIT_TIME = TimeUnit.MINUTES.toMillis(2);

  public static @NotNull String getTestName(@NotNull String name, boolean lowercaseFirstLetter) {
    name = StringUtil.trimStart(name, "test");
    return name.isEmpty() ? "" : lowercaseFirstLetter(name, lowercaseFirstLetter);
  }

  public static @NotNull String lowercaseFirstLetter(@NotNull String name, boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  public static boolean isAllUppercaseName(@NotNull String name) {
    var uppercaseChars = 0;
    for (var i = 0; i < name.length(); i++) {
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
  public static <T> void maskExtensions(
    @NotNull ProjectExtensionPointName<T> pointName,
    @NotNull Project project,
    @NotNull List<? extends T> newExtensions,
    @NotNull Disposable parentDisposable
  ) {
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
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, null, null);
  }

  public static @NotNull String print(@NotNull JTree tree, @NotNull TreePath path, @Nullable Queryable.PrintInfo printInfo, boolean withSelection) {
    return print(tree, path,  withSelection, printInfo, null, null);
  }

  public static @NotNull String print(@NotNull JTree tree, boolean withSelection, @Nullable Predicate<? super String> nodePrintCondition) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, nodePrintCondition, null);
  }

  public static @NotNull String print(@NotNull JTree tree, boolean withSelection, @Nullable Predicate<? super String> nodePrintCondition, @Nullable Function<PrintNodeInfo, PrintChildrenResult> beforeChildren) {
    return print(tree, new TreePath(tree.getModel().getRoot()), withSelection, null, nodePrintCondition, beforeChildren);
  }

  private static String print(
    JTree tree,
    TreePath path,
    boolean withSelection,
    @Nullable Queryable.PrintInfo printInfo,
    @Nullable Predicate<? super String> nodePrintCondition,
    @Nullable Function<PrintNodeInfo, PrintChildrenResult> beforeChildren
  ) {
    var strings = new ArrayList<String>();
    Predicate<Pair<Object, String>> condition = nodePrintCondition == null ? null : pair -> nodePrintCondition.test(pair.second);
    printImpl(tree, path, strings, 0, withSelection, printInfo, condition, beforeChildren);
    return String.join("\n", strings);
  }

  private static void printImpl(
    JTree tree,
    TreePath path,
    Collection<? super String> strings,
    int level,
    boolean withSelection,
    @Nullable Queryable.PrintInfo printInfo,
    @Nullable Predicate<Pair<Object, String>> nodePrintCondition,
    @Nullable Function<@NotNull PrintNodeInfo, @NotNull PrintChildrenResult> beforeChildren
  ) {
    var pathComponent = path.getLastPathComponent();
    var userObject = TreeUtil.getUserObject(pathComponent);
    var nodeText = toString(userObject, printInfo);

    if (nodePrintCondition != null && !nodePrintCondition.test(new Pair<>(userObject, nodeText))) {
      return;
    }

    var buff = new StringBuilder();
    buff.repeat(' ', level);

    var expanded = tree.isExpanded(path);
    var childCount = tree.getModel().getChildCount(pathComponent);

    PrintChildrenResult printChildrenResult = null;
    PrintChildrenResult.ChildrenAction childrenAction = PrintChildrenResult.ChildrenAction.VISIT;
    if (beforeChildren != null) {
      printChildrenResult = beforeChildren.apply(new PrintNodeInfo(userObject, nodeText, childCount));
      childrenAction = requireNonNull(printChildrenResult).Action;
    }

    if (childCount > 0 && childrenAction != PrintChildrenResult.ChildrenAction.REMOVE) {
      buff.append(expanded ? '-' : '+');
    }

    var selected = tree.getSelectionModel().isPathSelected(path);
    if (withSelection && selected) {
      buff.append('[');
    }

    buff.append(nodeText);

    if (withSelection && selected) {
      buff.append(']');
    }

    strings.add(buff.toString());

    if (expanded) {
      if (childrenAction == PrintChildrenResult.ChildrenAction.REPLACE) {
        assert printChildrenResult.ReplacementText != null : "Expected children replacement text for REPLACE_CHILDREN action, but got null";
        buff.setLength(0);
        buff.repeat(' ', level + 1);
        buff.append(printChildrenResult.ReplacementText);
        strings.add(buff.toString());
      } else if (childrenAction == PrintChildrenResult.ChildrenAction.VISIT) {
        for (var i = 0; i < childCount; i++) {
          var childPath = path.pathByAddingChild(tree.getModel().getChild(pathComponent, i));
          printImpl(tree, childPath, strings, level + 1, withSelection, printInfo, nodePrintCondition, beforeChildren);
        }
      }
    }
  }

  public static void assertTreeEqual(@NotNull JTree tree, @NotNull String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqual(@NotNull JTree tree, @NotNull String expected, boolean checkSelected) {
    assertTreeEqual(tree, expected, checkSelected, false);
  }

  public static void assertTreeEqual(@NotNull JTree tree, @NotNull String expected, boolean checkSelected, boolean ignoreOrder) {
    var treeStringPresentation = print(tree, checkSelected);
    if (ignoreOrder) {
      var actualLines = sorted(ContainerUtil.map(splitByLines(treeStringPresentation), String::trim));
      var expectedLines = sorted(ContainerUtil.map(splitByLines(expected), String::trim));
      assertEquals("Expected:\n" + expected + "\nActual:\n" + treeStringPresentation, expectedLines, actualLines);
    }
    else {
      assertSameLines(expected.trim(), treeStringPresentation.trim());
    }
  }

  public static void expand(@NotNull JTree tree, int @NotNull ... rows) {
    for (var row : rows) {
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
    waitForPromise(TreeUtil.promiseExpand(tree, Integer.MAX_VALUE, predicate));
  }

  private static long getMillisSince(long startTimeMillis) {
    return System.currentTimeMillis() - startTimeMillis;
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis) {
    assertMaxWaitTimeSince(startTimeMillis, MAX_WAIT_TIME);
  }

  private static void assertMaxWaitTimeSince(long startTimeMillis, long timeoutMillis) {
    var took = getMillisSince(startTimeMillis);
    if (took > timeoutMillis) {
      throw new AssertionError(
        "The waiting takes too long. " +
        "Expected to take no more than: " + timeoutMillis + " ms but took: " + took + " ms\n" +
        "Thread dump: " + ThreadDumper.dumpThreadsToString() + "\n" +
        "Coroutine dump: " + CoroutineDumperKt.dumpCoroutines(null, true, true) + "\n"
      );
    }
  }

  private static void assertDispatchThreadWithoutWriteAccess() {
    var application = ApplicationManager.getApplication();
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

  private static boolean isBusy(JTree tree, TreeModel model) {
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
    var startTimeMillis = System.currentTimeMillis();
    while (busyCondition.get()) {
      assertMaxWaitTimeSince(startTimeMillis);
      TimeoutUtil.sleep(5);
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
        UIUtil.dispatchAllInvocationEvents();
      });
    }
  }

  public static void waitForCallback(@NotNull ActionCallback callback) {
    var future = new CompletableFuture<>();
    callback.doWhenDone(() -> future.complete(null)).doWhenRejected(__ -> future.complete(null));
    waitForFuture(future);
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  public static <T> @Nullable T waitForPromise(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME, false);
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  public static <T> @Nullable T waitForPromise(@NotNull Promise<T> promise, long timeoutMillis) {
    return waitForPromise(promise, timeoutMillis, false);
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  public static <T> @Nullable T assertPromiseSucceeds(@NotNull Promise<T> promise) {
    return waitForPromise(promise, MAX_WAIT_TIME, true);
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  private static @Nullable <T> T waitForPromise(Promise<T> promise, long timeoutMillis, boolean assertSucceeded) {
    assertDispatchThreadWithoutWriteAccess();
    var start = System.currentTimeMillis();
    while (true) {
      if (promise.getState() == Promise.State.PENDING) {
        TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
          UIUtil.dispatchAllInvocationEvents();
        });
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
    if (!EDT.isCurrentThreadEdt()) {
      try {
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    assertDispatchThreadWithoutWriteAccess();
    var start = System.currentTimeMillis();
    while (true) {
      if (!future.isDone()) {
        dispatchAllInvocationEventsInIdeEventQueue();
      }
      try {
        return future.get(10, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) { }
      catch (Exception e) {
        throw new AssertionError(e);
      }
      assertMaxWaitTimeSince(start, timeoutMillis);
    }
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  public static void waitForAlarm(int delay) {
    var app = ApplicationManager.getApplication();
    assertDispatchThreadWithoutWriteAccess();

    var tempDisposable = Disposer.newDisposable();

    var runnableInvoked = new AtomicBoolean();
    var pooledRunnableInvoked = new AtomicBoolean();
    var alarmInvoked1 = new AtomicBoolean();
    var alarmInvoked2 = new AtomicBoolean();
    var alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, tempDisposable);
    var pooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, tempDisposable);
    var initialModality = ModalityState.current();

    alarm.addRequest(() -> {
      alarmInvoked1.set(true);
      app.invokeLater(() -> {
        runnableInvoked.set(true);
        alarm.addRequest(() -> alarmInvoked2.set(true), delay);
      });
    }, delay);
    pooledAlarm.addRequest(() -> pooledRunnableInvoked.set(true), delay);

    dispatchAllInvocationEventsInIdeEventQueue();

    var start = System.currentTimeMillis();
    try {
      var sleptAlready = false;
      while (!alarmInvoked2.get()) {
        var laterInvoked = new AtomicBoolean();
        app.invokeLater(() -> laterInvoked.set(true));
        dispatchAllInvocationEventsInIdeEventQueue();
        waitForAllDocumentsCommitted(10, TimeUnit.SECONDS);
        assertTrue(laterInvoked.get());

        TimeoutUtil.sleep(sleptAlready ? 10 : delay);
        sleptAlready = true;
        if (getMillisSince(start) > MAX_WAIT_TIME) {
          var queue = ((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).dumpQueue();
          throw new AssertionError(
            "Couldn't await alarm" +
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
    dispatchAllInvocationEventsInIdeEventQueue();
  }

  /**
   * Dispatch all pending invocation events (if any) in the {@link IdeEventQueue}, ignores and removes all other events from the queue.
   * Should only be invoked in Swing thread (asserted inside {@link IdeEventQueue#dispatchEvent(AWTEvent)})
   */
  public static void dispatchAllInvocationEventsInIdeEventQueue() {
    assertDispatchThreadWithoutWriteAccess();
    var eventQueue = IdeEventQueue.getInstance();
    ThreadContext.resetThreadContext(() -> {
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
        // due to non-blocking acquisition of write-intent, `NonBlockingFlushQueue` can appear in the state
        // where it has stuck WI runnables. This method is called to ensure that _all_ runnables are dispatched,
        // so we also want to wait for WI runnables here
        var canary = new AtomicBoolean(false);
        ApplicationManager.getApplication().invokeLater(() -> canary.set(true), ModalityState.any());
        while (true) {
          var event = eventQueue.peekEvent();
          if (event == null && canary.get()) break;
          event = eventQueue.getNextEvent();
          if (event instanceof InvocationEvent) {
            eventQueue.dispatchEvent(event);
          }
        }
      });
      return null;
    });
  }

  @TestOnly
  @SuppressWarnings("UsagesOfObsoleteApi")
  public static void waitForSingleAlarm(@NotNull SingleAlarm alarm, long timeout, @NotNull TimeUnit timeUnit) throws TimeoutException {
    var job = alarm.getCurrentJob();
    if (job == null) {
      return;
    }

    var currentTime = System.currentTimeMillis();
    while (true) {
      if (!job.isActive()) {
        return;
      }
      if (getMillisSince(currentTime) > timeUnit.toMillis(timeout)) {
        throw new TimeoutException("Could not wait for " + alarm + "to finish");
      }
      dispatchAllEventsInIdeEventQueue();
    }
  }

  /**
   * Dispatch all pending events (if any) in the {@link IdeEventQueue}. Should only be invoked from EDT.
   */
  public static void dispatchAllEventsInIdeEventQueue() {
    EdtTestUtilKt.dispatchAllEventsInIdeEventQueue();
  }

  /**
   * Dispatch one pending event (if any) in the {@link IdeEventQueue}. Should only be invoked from EDT.
   */
  public static AWTEvent dispatchNextEventIfAny() {
    return EdtTestUtilKt.dispatchNextEventIfAny();
  }

  public static @NotNull StringBuilder print(
    @NotNull AbstractTreeStructure structure,
    @NotNull Object node,
    int currentLevel,
    @Nullable Comparator<?> comparator,
    int maxRowCount,
    char paddingChar,
    @Nullable Queryable.PrintInfo printInfo
  ) {
    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, o -> toString(o, printInfo));
  }

  public static @NotNull String print(
    @NotNull AbstractTreeStructure structure,
    @NotNull Object node,
    @NotNull Function<Object, String> nodePresenter
  ) {
    return print(structure, node, 0, Comparator.comparing(nodePresenter), -1, ' ', nodePresenter).toString();
  }

  private static StringBuilder print(
    AbstractTreeStructure structure,
    Object node,
    int currentLevel,
    @Nullable Comparator<?> comparator,
    int maxRowCount,
    char paddingChar,
    Function<Object, String> nodePresenter
  ) {
    var buffer = new StringBuilder();
    doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, 0, paddingChar, nodePresenter);
    return buffer;
  }

  private static int doPrint(
    StringBuilder buffer,
    int currentLevel,
    Object node,
    AbstractTreeStructure structure,
    @Nullable Comparator<?> comparator,
    int maxRowCount,
    int currentLine,
    char paddingChar,
    Function<Object, String> nodePresenter
  ) {
    if (currentLine >= maxRowCount && maxRowCount != -1) return currentLine;

    buffer.repeat(paddingChar, currentLevel);
    buffer.append(nodePresenter.apply(node)).append("\n");
    currentLine++;
    var children = structure.getChildElements(node);

    if (comparator != null) {
      var list = new ArrayList<>(List.of(children));
      @SuppressWarnings("unchecked")
      var c = (Comparator<Object>)comparator;
      list.sort(c);
      children = ArrayUtil.toObjectArray(list);
    }
    for (var child : children) {
      currentLine = doPrint(buffer, currentLevel + 1, child, structure, comparator, maxRowCount, currentLine, paddingChar, nodePresenter);
    }

    return currentLine;
  }

  public static @NotNull String print(Object @NotNull [] objects) {
    return print(List.of(objects));
  }

  public static @NotNull String print(@NotNull Collection<?> c) {
    return c.stream().map(each -> toString(each, null)).collect(Collectors.joining("\n"));
  }

  public static @NotNull String print(@NotNull ListModel<?> model) {
    var result = new StringBuilder();
    for (var i = 0; i < model.getSize(); i++) {
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
  @SuppressWarnings("UsagesOfObsoleteApi")
  public static void invokeNamedAction(@NotNull String actionId) {
    var action = ActionManager.getInstance().getAction(actionId);
    assertNotNull(action);
    @SuppressWarnings("deprecation") var context = DataManager.getInstance().getDataContext();
    var event = AnActionEvent.createEvent(action, context, null, "", ActionUiKind.NONE, null);
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, event);
    ActionUtil.updateAction(action, event);
    assertTrue(event.getPresentation().isEnabled());
    ActionUtil.performAction(action, event);
  }

  public static void assertTiming(@NotNull String message, long expectedMillis, long actualMillis) {
    if (COVERAGE_ENABLED_BUILD) return;

    var expectedOnMyMachine = Math.max(1, expectedMillis * Timings.CPU_TIMING / Timings.REFERENCE_CPU_TIMING);

    // Allow 10% more in case of test machine is busy.
    var logMessage = message;
    if (actualMillis > expectedOnMyMachine) {
      var percentage = (int)(100.0 * (actualMillis - expectedOnMyMachine) / expectedOnMyMachine);
      logMessage += ". Operation took " + percentage + "% longer than expected";
    }
    logMessage += ". Expected on my machine: " + expectedOnMyMachine + "." +
                  " Actual: " + actualMillis + "." +
                  " Expected on Standard machine: " + expectedMillis + ";" +
                  " Timings: CPU=" + Timings.CPU_TIMING +
                  ", I/O=" + Timings.IO_TIMING + ".";
    var acceptableChangeFactor = 1.1;
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
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public static @NotNull BenchmarkTestInfo newBenchmark(@NotNull String launchName, @NotNull ThrowableRunnable<?> test) {
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
  public static @NotNull BenchmarkTestInfo newBenchmarkWithVariableInputSize(
    @NotNull String launchName,
    int expectedInputSize,
    @NotNull ThrowableComputable<Integer, ?> test
  ) {
    return BenchmarkTestInfoLoader.Companion.getInstance().initialize(test, expectedInputSize, launchName);
  }

  public static void assertPathsEqual(@Nullable String expected, @Nullable String actual) {
    if (expected != null) expected = FileUtilRt.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtilRt.toSystemIndependentName(actual);
    assertEquals(expected, actual);
  }

  public static @NotNull String getJavaExe() {
    return SystemProperties.getJavaHome() + (OS.CURRENT == OS.Windows ? "\\bin\\java.exe" : "/bin/java");
  }

  public static @NotNull URL getRtJarURL() {
    var home = SystemProperties.getJavaHome();
    try {
      return new URI("jrt:" + home).toURL();
    }
    catch (MalformedURLException | URISyntaxException e) {
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
    // A more liberal threshold helps avoid unnecessary waits if only tiny userspace slices occur.
    // Configurable via system property: idea.test.waitForAllBackgroundCalm.userMsThreshold (default: 10 ms).
    long thresholdMs = Long.getLong("idea.test.waitForAllBackgroundCalm.userMsThreshold", 10L);
    for (var i = 0; i < 50; i++) {
      var data = CpuUsageData.measureCpuUsage(() -> TimeoutUtil.sleep(100));
      if (!data.hasAnyActivityBesides(Thread.currentThread(), Math.max(0L, thresholdMs))) {
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
      var duration = TimeoutUtil.measureExecutionTime(actionToMeasure::run);
      try {
        assertTiming(message, expectedMillis, duration);
        break;
      }
      catch (AssertionFailedError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
        var s = e.getMessage() + "\n  " + attempts + " " + StringUtil.pluralize("attempt", attempts) + " remain";
        TeamCityLogger.warning(s, null);
        System.err.println(s);
      }
    }
  }

  private static @NotNull Map<String, VirtualFile> buildNameToFileMap(
    VirtualFile[] files,
    @Nullable VirtualFileFilter filter,
    @Nullable Function<VirtualFile, String> fileNameMapper
  ) {
    var map = new HashMap<String, VirtualFile>();
    for (var file : files) {
      if (filter != null && !filter.accept(file)) continue;
      var fileName = fileNameMapper != null ? fileNameMapper.apply(file) : file.getName();
      map.put(fileName, file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(@NotNull VirtualFile dirExpected, @NotNull VirtualFile dirActual) throws IOException {
    assertDirectoriesEqual(dirExpected, dirActual, null);
  }

  public static void assertDirectoriesEqual(
    @NotNull VirtualFile dirExpected,
    @NotNull VirtualFile dirActual,
    @Nullable VirtualFileFilter fileFilter
  ) throws IOException {
    assertDirectoriesEqual(dirExpected, dirActual, fileFilter, null);
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  public static void assertDirectoriesEqual(
    @NotNull VirtualFile dirExpected,
    @NotNull VirtualFile dirActual,
    @Nullable VirtualFileFilter fileFilter,
    @Nullable Function<VirtualFile, String> fileNameMapper
  ) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    var childrenAfter = dirExpected.getChildren();
    shallowCompare(dirExpected, childrenAfter);

    var childrenBefore = dirActual.getChildren();
    shallowCompare(dirActual, childrenBefore);

    var mapAfter = buildNameToFileMap(childrenAfter, fileFilter, fileNameMapper);
    var mapBefore = buildNameToFileMap(childrenBefore, fileFilter, fileNameMapper);

    var keySetAfter = mapAfter.keySet();
    var keySetBefore = mapBefore.keySet();
    assertEquals(dirExpected.getPath(), keySetAfter, keySetBefore);

    for (var name : keySetAfter) {
      var fileAfter = mapAfter.get(name);
      var fileBefore = mapBefore.get(name);
      if (fileAfter.isDirectory()) {
        assertDirectoriesEqual(fileAfter, fileBefore, fileFilter, fileNameMapper);
      }
      else if (FileTypeRegistry.getInstance().findFileTypeByName(fileAfter.getName()) == ArchiveFileType.INSTANCE) {
        assertJarFilesEqual(fileAfter, fileBefore);
      }
      else {
        assertFilesEqual(fileAfter, fileBefore);
      }
    }
  }

  private static void shallowCompare(VirtualFile dir, VirtualFile[] vfs) {
    if (dir.isInLocalFileSystem() && dir.getFileSystem() != TempFileSystem.getInstance()) {
      var vfsPaths = Stream.of(vfs).map(VirtualFile::getPath).sorted().toList();
      var ioPaths = NioFiles.list(dir.toNioPath()).stream().map(Path::toString).map(FileUtilRt::toSystemIndependentName).sorted().toList();
      assertEquals(vfsPaths, ioPaths);
    }
  }

  public static void assertFilesEqual(@NotNull VirtualFile fileExpected, @NotNull VirtualFile fileActual) throws IOException {
    var actual = fileText(fileActual);
    var expected = fileText(fileExpected);
    if (expected == null || actual == null) {
      assertArrayEquals(fileExpected.getPath(), fileExpected.contentsToByteArray(), fileActual.contentsToByteArray());
    }
    else if (!StringUtil.equals(expected, actual)) {
      throw new FileComparisonFailedError(
        "Text mismatch in the file " + fileExpected.getName(), expected, actual,
        fileActual.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH));
    }
  }

  private static String fileText(@NotNull VirtualFile file) throws IOException {
    var doc = FileDocumentManager.getInstance().getDocument(file);
    if (doc != null) {
      return doc.getText();
    }
    if (!file.getFileType().isBinary() || FileTypeRegistry.getInstance().isFileOfType(file, FileTypes.UNKNOWN)) {
      return LoadTextUtil.getTextByBinaryPresentation(file.contentsToByteArray(false), file).toString();
    }
    return null;
  }

  public static void assertJarFilesEqual(@NotNull VirtualFile fileExpected, @NotNull VirtualFile fileActual) throws IOException {
    assertJarFilesEqual(fileExpected.toNioPath(), fileActual.toNioPath());
  }

  private static void assertJarFilesEqual(Path file1, Path file2) throws IOException {
    var tempDir = Files.createTempDirectory("assert_jar_tmp_");
    try {
      var tempDirectory1 = Files.createDirectory(tempDir.resolve("tmp1"));
      var tempDirectory2 = Files.createDirectory(tempDir.resolve("tmp2"));

      new Decompressor.Zip(file1).extract(tempDirectory1);
      new Decompressor.Zip(file2).extract(tempDirectory2);

      var dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDirectory1);
      assertNotNull(tempDirectory1.toString(), dirAfter);
      var dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDirectory2);
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

  public static @NotNull @SystemDependent String getCommunityPath() {
    var homePath = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy());
    if (Files.isDirectory(homePath.resolve("community/.idea"))) {
      homePath = homePath.resolve("community");
    }
    return homePath.toString();
  }

  public static @NotNull @SystemIndependent String getPlatformTestDataPath() {
    return getCommunityPath().replace('\\', '/') + "/platform/platform-tests/testData/";
  }

  @Contract(pure = true)
  public static @NotNull Comparator<AbstractTreeNode<?>> createComparator(Queryable.PrintInfo printInfo) {
    return (o1, o2) -> {
      var displayText1 = o1.toTestString(printInfo);
      var displayText2 = o2.toTestString(printInfo);
      return Comparing.compare(displayText1, displayText2);
    };
  }

  public static @NotNull String loadFileText(@NotNull String fileName) throws IOException {
    return StringUtil.convertLineSeparators(Files.readString(Path.of(fileName)));
  }

  /** @deprecated use {@link #withEncoding(Charset, ThrowableRunnable)} instead */
  @Deprecated(forRemoval = true)
  public static void withEncoding(@NotNull String encoding, @NotNull ThrowableRunnable<?> r) {
    withEncoding(Charset.forName(encoding), r);
  }

  public static void withEncoding(@NotNull Charset encoding, @NotNull ThrowableRunnable<?> r) {
    try {
      var oldCharset = Charset.defaultCharset();
      try {
        patchSystemFileEncoding(encoding.name());
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
    var std = System.err;
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
      var output = ExecUtil.execAndGetOutput(command.withRedirectErrorStream(true));
      assertEquals(output.getStdout(), 0, output.getExitCode());
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public static @NotNull List<WebReference> collectWebReferences(@NotNull PsiElement element) {
    var refs = new ArrayList<WebReference>();
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        for (var ref : element.getReferences()) {
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
    var result = new SmartList<UrlReference>();
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
      @SuppressWarnings("unchecked") T t = (T)reference;
      return t;
    }
    if (reference instanceof PsiMultiReference) {
      var psiReferences = ((PsiMultiReference)reference).getReferences();
      for (var psiReference : psiReferences) {
        if (refType.isInstance(psiReference)) {
          @SuppressWarnings("unchecked") T t = (T)psiReference;
          return t;
        }
      }
    }
    throw new AssertionError("given reference should be " + refType + " but " + (reference != null ? reference.getClass() : null) + " was given");
  }

  public static void registerProjectCleanup(@NotNull Runnable cleanup) {
    ourProjectCleanups.add(cleanup);
  }

  public static void cleanupAllProjects() {
    for (var each : ourProjectCleanups) {
      each.run();
    }
    ourProjectCleanups.clear();
  }

  public static <T> void assertComparisonContractNotViolated(
    @NotNull List<? extends T> values,
    @NotNull Comparator<? super T> comparator,
    @NotNull BiPredicate<? super T, ? super T> equality
  ) {
    for (var i1 = 0; i1 < values.size(); i1++) {
      for (var i2 = i1; i2 < values.size(); i2++) {
        var value1 = values.get(i1);
        var value2 = values.get(i2);

        var result12 = comparator.compare(value1, value2);
        var result21 = comparator.compare(value2, value1);
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

        for (var i3 = i2; i3 < values.size(); i3++) {
          var value3 = values.get(i3);

          var result23 = comparator.compare(value2, value3);
          var result31 = comparator.compare(value3, value1);

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
    ((FileTemplateManagerImpl)FileTemplateManager.getInstance(project)).setDefaultFileIncludeTemplateTextTemporarilyForTest(
      FileTemplateManager.FILE_HEADER_TEMPLATE_NAME,
      """
      /**
       * Created by ${USER} on ${DATE}.
       */
      """,
      parentDisposable);
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
    var dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, element.getProject())
      .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
      .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
      .build();

    var cc = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    var configuration = producer.createConfigurationFromContext(cc);
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
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    long timeoutInSeconds
  ) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, DefaultRunExecutor.EXECUTOR_ID, timeoutInSeconds);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId}, then waits for 60 seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId
  ) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, executorId, 60);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId},
   * then waits for the {@code timeoutInSeconds} seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId,
    long timeoutInSeconds
  ) throws InterruptedException {
    var result = executeConfiguration(runConfiguration, executorId, null);
    var processHandler = result.second.getProcessHandler();
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
    @Nullable Consumer<? super RunContentDescriptor> contentDescriptorProcessor
  ) throws InterruptedException {
    var executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
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
    var project = runConfiguration.getProject();
    var factory = runConfiguration.getFactory();
    if (factory == null) {
      fail("No factory found for: " + runConfiguration);
    }
    var runnerAndConfigurationSettings = RunManager.getInstance(project).createConfiguration(runConfiguration, factory);
    var runner = ProgramRunner.getRunner(executor.getId(), runConfiguration);
    if (runner == null) {
      fail("No runner found for: " + executor.getId() + " and " + runConfiguration);
    }
    var refRunContentDescriptor = new Ref<RunContentDescriptor>();
    var executionEnvironment = new ExecutionEnvironment(executor, runner, runnerAndConfigurationSettings, project);
    var failure = new boolean[]{false};
    ProgramRunnerUtil.executeConfigurationAsync(executionEnvironment, false, false, new ProgramRunner.Callback() {
      @Override
      public void processNotStarted(@Nullable Throwable error) {
        failure[0] = true;
      }

      @Override
      public void processStarted(RunContentDescriptor descriptor) {
        var processHandler = descriptor.getProcessHandler();
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
  public static void waitWithEventsDispatching(
    @NotNull Supplier<String> errorMessageSupplier,
    @NotNull BooleanSupplier condition,
    int timeoutInSeconds,
    @Nullable Runnable callback
  ) {
    var start = System.currentTimeMillis();
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
    var filePath = Path.of(requireNonNull(project.getBasePath(), () -> project.toString()), fileRelativePath);
    var virtualFile = LocalFileSystem.getInstance().findFileByNioFile(filePath);
    if (virtualFile == null || !virtualFile.exists()) {
      throw new IllegalArgumentException(String.format("File '%s' doesn't exist", filePath));
    }
    var psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }
    var offset = psiFile.getText().indexOf(signature);
    return psiFile.findElementAt(offset);
  }

  public static void useAppConfigDir(@NotNull ThrowableRunnable<? extends Exception> task) throws Exception {
    var configDir = PathManager.getConfigDir();
    Path configCopy;
    if (Files.exists(configDir)) {
      configCopy = Files.move(configDir, Paths.get(configDir + "_bak"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    else {
      NioFiles.deleteRecursively(configDir);
      configCopy = null;
    }

    try {
      task.run();
    }
    finally {
      NioFiles.deleteRecursively(configDir);
      if (configCopy != null) {
        Files.move(configCopy, configDir, StandardCopyOption.ATOMIC_MOVE);
      }
    }
  }

  public static @NotNull Project loadAndOpenProject(@NotNull Path path, @NotNull Disposable parent) {
    var project = requireNonNull(ProjectManagerEx.getInstanceEx().openProject(path, new OpenProjectTaskBuilder().build()));
    Disposer.register(parent, () -> forceCloseProjectWithoutSaving(project));
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    return project;
  }

  public static boolean isUnderCommunityClassPath() {
    // StdFileTypes.JSPX is assigned to PLAIN_TEXT in IDEA Community
    return FileTypeManager.getInstance().getStdFileType("JSPX") == FileTypes.PLAIN_TEXT;
  }

  public static <E extends Throwable> void withSystemProperty(@NotNull String key, @Nullable String value, @NotNull ThrowableRunnable<E> task) throws E {
    var original = value != null ? System.setProperty(key, value) : System.clearProperty(key);
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
    var N = Math.min(
      Runtime.getRuntime().availableProcessors(),
      Math.min(ForkJoinPool.getCommonPoolParallelism(), ForkJoinPool.commonPool().getParallelism())
    );
    if (N < 4) {
      throw new AssumptionViolatedException(
        "not enough parallelism, couldn't test parallel performance: " +
        "available CPU cores=" + Runtime.getRuntime().availableProcessors() +
        "; FJP configured parallelism=" + ForkJoinPool.getCommonPoolParallelism() +
        "; FJP actual common pool parallelism=" + ForkJoinPool.commonPool().getParallelism());
    }
  }

  @TestOnly
  public static void waitForAllDocumentsCommitted(long timeout, @NotNull TimeUnit timeUnit) {
    var documentCommitThread = DocumentCommitThread.getInstance();
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
      documentCommitThread.waitForAllCommits(timeout, timeUnit);
    });
    // some callbacks on document commit might require EDT. So we forcibly dispatch pending events to run these callbacks
    dispatchAllInvocationEventsInIdeEventQueue();
  }
}
