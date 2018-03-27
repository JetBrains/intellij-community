/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testFramework;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.StoreUtil;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
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
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.Equality;
import junit.framework.AssertionFailedError;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.jar.JarFile;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

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

  public static <T> void registerExtension(@NotNull ExtensionsArea area, @NotNull ExtensionPointName<T> name, @NotNull final T t, @NotNull Disposable parentDisposable) {
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(name.getName());
    extensionPoint.registerExtension(t);
    Disposer.register(parentDisposable, () -> extensionPoint.unregisterExtension(t));
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
    return print(tree, tree.getModel().getRoot(), withSelection, null, null);
  }

  public static String print(JTree tree, Object root, @Nullable Queryable.PrintInfo printInfo, boolean withSelection) {
    return print(tree, root,  withSelection, printInfo, null);
  }

  public static String print(JTree tree, boolean withSelection, @Nullable Condition<String> nodePrintCondition) {
    return print(tree, tree.getModel().getRoot(), withSelection, null, nodePrintCondition);
  }

  private static String print(JTree tree, Object root,
                             boolean withSelection,
                             @Nullable Queryable.PrintInfo printInfo,
                             @Nullable Condition<String> nodePrintCondition) {
    StringBuilder buffer = new StringBuilder();
    final Collection<String> strings = printAsList(tree, root, withSelection, printInfo, nodePrintCondition);
    for (String string : strings) {
      buffer.append(string).append("\n");
    }
    return buffer.toString();
  }

  private static Collection<String> printAsList(JTree tree, Object root, boolean withSelection, @Nullable Queryable.PrintInfo printInfo,
                                                Condition<String> nodePrintCondition) {
    Collection<String> strings = new ArrayList<>();
    printImpl(tree, root, strings, 0, withSelection, printInfo, nodePrintCondition);
    return strings;
  }

  private static void printImpl(JTree tree,
                                Object root,
                                Collection<String> strings,
                                int level,
                                boolean withSelection,
                                @Nullable Queryable.PrintInfo printInfo,
                                @Nullable Condition<String> nodePrintCondition) {
    DefaultMutableTreeNode dmt = (DefaultMutableTreeNode)root;

    Object userObject = dmt.getUserObject();
    String nodeText = toString(userObject, printInfo);

    if (nodePrintCondition != null && !nodePrintCondition.value(nodeText)) return;

    StringBuilder buff = new StringBuilder();
    StringUtil.repeatSymbol(buff, ' ', level);

    boolean expanded = tree.isExpanded(new TreePath(dmt.getPath()));
    if (!dmt.isLeaf() && (tree.isRootVisible() || dmt != tree.getModel().getRoot() || dmt.getChildCount() > 0)) {
      buff.append(expanded ? "-" : "+");
    }

    boolean selected = tree.getSelectionModel().isPathSelected(new TreePath(dmt.getPath()));
    if (withSelection && selected) {
      buff.append("[");
    }

    buff.append(nodeText);

    if (withSelection && selected) {
      buff.append("]");
    }

    strings.add(buff.toString());

    int childCount = tree.getModel().getChildCount(root);
    if (expanded) {
      for (int i = 0; i < childCount; i++) {
        printImpl(tree, tree.getModel().getChild(root, i), strings, level + 1, withSelection, printInfo, nodePrintCondition);
      }
    }
  }

  public static void assertTreeEqual(JTree tree, @NonNls String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqualIgnoringNodesOrder(JTree tree, @NonNls String expected) {
    final Collection<String> actualNodesPresentation = printAsList(tree, tree.getModel().getRoot(), false, null, null);
    final List<String> expectedNodes = StringUtil.split(expected, "\n");
    UsefulTestCase.assertSameElements(actualNodesPresentation, expectedNodes);
  }

  public static void assertTreeEqual(JTree tree, String expected, boolean checkSelected) {
    String treeStringPresentation = print(tree, checkSelected);
    Assert.assertEquals(expected, treeStringPresentation);
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
    assert getMillisSince(startTimeMillis) <= MAX_WAIT_TIME : "the waiting takes too long";
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

  public static void pumpInvocationEventsFor(long duration, @NotNull TimeUnit unit) {
    pumpInvocationEventsFor(unit.toMillis(duration));
  }

  public static void pumpInvocationEventsFor(long millis) {
    assert 0 <= millis && millis <= MAX_WAIT_TIME;
    assertDispatchThreadWithoutWriteAccess();
    long startTimeMillis = System.currentTimeMillis();
    UIUtil.dispatchAllInvocationEvents();
    while (getMillisSince(startTimeMillis) <= millis) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  public static void waitForCallback(@NotNull ActionCallback callback) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    callback.doWhenDone(() -> promise.setResult(null));
    waitForPromise(promise);
  }

  @Nullable
  public static <T> T waitForPromise(@NotNull Promise<T> promise) {
    assertDispatchThreadWithoutWriteAccess();
    AtomicBoolean complete = new AtomicBoolean(false);
    promise.processed(ignore -> complete.set(true));
    T result = null;
    long start = System.currentTimeMillis();
    do {
      UIUtil.dispatchAllInvocationEvents();
      try {
        result = promise.blockingGet(20, TimeUnit.MILLISECONDS);
      }
      catch (Exception ignore) {
      }
      assertMaxWaitTimeSince(start);
    }
    while (!complete.get());
    UIUtil.dispatchAllInvocationEvents();
    return result;
  }

  /**
   * @see #pumpInvocationEventsFor(long)
   */
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
        Assert.assertTrue(laterInvoked.get());

        TimeoutUtil.sleep(sleptAlready ? 10 : delay);
        sleptAlready = true;
        if (getMillisSince(start) > MAX_WAIT_TIME) {
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
                                   "\n delayQueue=" + StringUtil.trimLog(((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).dumpQueue(), 1000) +
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
   * Dispatch all pending invocation events (if any) in the {@link IdeEventQueue}.
   * Should only be invoked in Swing thread (asserted inside {@link IdeEventQueue#dispatchEvent(AWTEvent)})
   */
  public static void dispatchAllInvocationEventsInIdeEventQueue() throws InterruptedException {
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      AWTEvent event1 = eventQueue.getNextEvent();
      if (event1 instanceof InvocationEvent) {
        eventQueue.dispatchEvent(event1);
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

  public static void assertTreeStructureEquals(@NotNull TreeModel treeModel, @NotNull String expected) {
    Assert.assertEquals(expected.trim(), print(createStructure(treeModel), treeModel.getRoot(), 0, null, -1, ' ', (Queryable.PrintInfo)null).toString().trim());
  }

  @NotNull
  protected static AbstractTreeStructure createStructure(@NotNull TreeModel treeModel) {
    return new AbstractTreeStructure() {
      @Override
      public Object getRootElement() {
        return treeModel.getRoot();
      }

      @Override
      public Object[] getChildElements(Object element) {
        return TreeUtil.nodeChildren(element, treeModel).toList().toArray();
      }

      @Nullable
      @Override
      public Object getParentElement(Object element) {
        return ((AbstractTreeNode)element).getParent();
      }

      @NotNull
      @Override
      public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void commit() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean hasSomethingToCommit() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static void invokeNamedAction(final String actionId) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    Assert.assertNotNull(action);
    final Presentation presentation = new Presentation();
    @SuppressWarnings("deprecation") final DataContext context = DataManager.getInstance().getDataContext();
    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);
    action.beforeActionPerformedUpdate(event);
    Assert.assertTrue(presentation.isEnabled());
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
   * example usage: {@code startPerformanceTest("calculating pi",100, testRunnable).cpuBound().assertTiming();}
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public static TestInfo startPerformanceTest(@NonNls @NotNull String what, int expectedMs, @NotNull ThrowableRunnable test) {
    return new TestInfo(test, expectedMs, what);
  }

  public static void assertPathsEqual(@Nullable String expected, @Nullable String actual) {
    if (expected != null) expected = FileUtil.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtil.toSystemIndependentName(actual);
    Assert.assertEquals(expected, actual);
  }

  @NotNull
  public static String getJavaExe() {
    return SystemProperties.getJavaHome() + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");
  }

  @NotNull
  public static String getRtJarPath() {
    String home = SystemProperties.getJavaHome();
    return SystemInfo.isAppleJvm ? FileUtil.toCanonicalPath(home + "/../Classes/classes.jar") : home + "/lib/rt.jar";
  }

  public static void saveProject(@NotNull Project project) {
    ProjectManagerEx.getInstanceEx().flushChangedProjectFileAlarm();
    StoreUtil.save(ServiceKt.getStateStore(project), project);
  }

  public static class TestInfo {
    private final ThrowableRunnable test; // runnable to measure
    private final int expectedMs;           // millis the test is expected to run
    private ThrowableRunnable setup;      // to run before each test
    private int usedReferenceCpuCores = 1;
    private int attempts = 4;             // number of retries if performance failed
    private final String what;         // to print on fail
    private boolean adjustForIO = false;   // true if test uses IO, timings need to be re-calibrated according to this agent disk performance
    private boolean adjustForCPU = true;  // true if test uses CPU, timings need to be re-calibrated according to this agent CPU speed
    private boolean useLegacyScaling;

    static {
      // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
      IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    }

    private TestInfo(@NotNull ThrowableRunnable test, int expectedMs, @NotNull String what) {
      this.test = test;
      this.expectedMs = expectedMs;
      assert expectedMs > 0 : "Expected must be > 0. Was: "+ expectedMs;
      this.what = what;
    }

    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo setup(@NotNull ThrowableRunnable setup) { assert this.setup==null; this.setup = setup; return this; }

    /**
     * Invoke this method if and only if the code under performance tests is using all CPU cores.
     * The "standard" expected time then should be given for a machine which has 8 CPU cores.
     * Actual test expected time will be adjusted according to the number of cores the actual computer has.
     */
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo usesAllCPUCores() { return usesMultipleCPUCores(8); }

    /**
     * Invoke this method if and only if the code under performance tests is using {@code maxCores} CPU cores (or less if the computer has less).
     * The "standard" expected time then should be given for a machine which has {@code maxCores} CPU cores.
     * Actual test expected time will be adjusted according to the number of cores the actual computer has.
     */
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo usesMultipleCPUCores(int maxCores) { assert adjustForCPU : "This test configured to be io-bound, it cannot use all cores"; usedReferenceCpuCores = maxCores; return this; }

    /**
     * @deprecated tests are CPU-bound by default, so no need to call this method.
     */
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    @Deprecated
    public TestInfo cpuBound() { adjustForIO = false; adjustForCPU = true; return this; }
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo ioBound() { adjustForIO = true; adjustForCPU = false; return this; }
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo attempts(int attempts) { this.attempts = attempts; return this; }
    /**
     * @deprecated Enables procedure for nonlinear scaling of results between different machines. This was historically enabled, but doesn't
     * seem to be meaningful, and is known to make results worse in some cases. Consider migration off this setting, recalibrating
     * expected execution time accordingly.
     */
    @Contract(pure = true) // to warn about not calling .assertTiming() in the end
    public TestInfo useLegacyScaling() { useLegacyScaling = true; return this; }

    public void assertTiming() {
      assert expectedMs != 0 : "Must call .expect() before run test";
      if (COVERAGE_ENABLED_BUILD) return;
      Timings.getStatistics(); // warm-up, measure

      if (attempts == 1) {
        System.gc();
      }

      while (true) {
        attempts--;
        CpuUsageData data;
        try {
          if (setup != null) setup.run();
          waitForAllBackgroundActivityToCalmDown();
          data = CpuUsageData.measureCpuUsage(test);
        }
        catch (RuntimeException|Error throwable) {
          throw throwable;
        }
        catch (Throwable throwable) {
          throw new RuntimeException(throwable);
        }
        long duration = data.durationMs;

        int expectedOnMyMachine = expectedMs;
        if (adjustForCPU) {
          int coreCountUsedHere = usedReferenceCpuCores < 8 ? Math.min(JobSchedulerImpl.getJobPoolParallelism(), usedReferenceCpuCores) : JobSchedulerImpl.getJobPoolParallelism();
          expectedOnMyMachine *= usedReferenceCpuCores;
          expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.CPU_TIMING, Timings.REFERENCE_CPU_TIMING, useLegacyScaling);
          expectedOnMyMachine /= coreCountUsedHere;
        }
        if (adjustForIO) {
          expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.IO_TIMING, Timings.REFERENCE_IO_TIMING, useLegacyScaling);
        }

        // Allow 10% more in case of test machine is busy.
        double acceptableChangeFactor = 1.1;
        int percentage = (int)(100.0 * (duration - expectedOnMyMachine) / expectedOnMyMachine);
        String colorCode = duration < expectedOnMyMachine ? "32;1m" : // green
                        duration < expectedOnMyMachine * acceptableChangeFactor ? "33;1m" : // yellow
                        "31;1m"; // red
        String logMessage = String.format(
          "%s took \u001B[%s%d%% %s time\u001B[0m than expected" +
          "\n  Expected: %sms (%s)" +
          "\n  Actual:   %sms (%s)" +
          "\n  Timings:  %s" +
          "\n  Threads:  %s" +
          "\n  GC stats: %s",
          what, colorCode, Math.abs(percentage), percentage > 0 ? "more" : "less",
          expectedOnMyMachine, StringUtil.formatDuration(expectedOnMyMachine),
          duration, StringUtil.formatDuration(duration),
          Timings.getStatistics(),
          data.getThreadStats(),
          data.getGcStats());

        if (duration < expectedOnMyMachine) {
          TeamCityLogger.info(logMessage);
          System.out.println("\nSUCCESS: " + logMessage);
        }
        else if (duration < expectedOnMyMachine * acceptableChangeFactor) {
          TeamCityLogger.warning(logMessage, null);
          System.out.println("\nWARNING: " + logMessage);
        }
        else {
          // try one more time
          if (attempts == 0) {
            //try {
            //  Object result = Class.forName("com.intellij.util.ProfilingUtil").getMethod("captureCPUSnapshot").invoke(null);
            //  System.err.println("CPU snapshot captured in '"+result+"'");
            //}
            //catch (Exception e) {
            //}

            throw new AssertionFailedError(logMessage);
          }
          System.gc();
          System.gc();
          System.gc();
          String s = logMessage + "\n  " + attempts + " attempts remain";
          TeamCityLogger.warning(s, null);
          if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            System.err.println(s);
          }
          //if (attempts == 1) {
          //  try {
          //    Class.forName("com.intellij.util.ProfilingUtil").getMethod("startCPUProfiling").invoke(null);
          //  }
          //  catch (Exception e) {
          //  }
          //}
          continue;
        }
        break;
      }
    }

    private static int adjust(int expectedOnMyMachine, long thisTiming, long referenceTiming, boolean useLegacyScaling) {
      if (useLegacyScaling) {
        double speed = 1.0 * thisTiming / referenceTiming;
        double delta = speed < 1
                       ? 0.9 + Math.pow(speed - 0.7, 2)
                       : 0.45 + Math.pow(speed - 0.25, 2);
        expectedOnMyMachine *= delta;
        return expectedOnMyMachine;
      }
      else {
        return (int)(expectedOnMyMachine * thisTiming / referenceTiming);
      }
    }
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

  private static long measure(@NotNull Runnable actionToMeasure) {
    waitForAllBackgroundActivityToCalmDown();
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
        String s = e.getMessage() + "\n  " + attempts + " attempts remain";
        TeamCityLogger.warning(s, null);
        System.err.println(s);
      }
    }
  }

  private static HashMap<String, VirtualFile> buildNameToFileMap(VirtualFile[] files, @Nullable VirtualFileFilter filter) {
    HashMap<String, VirtualFile> map = new HashMap<>();
    for (VirtualFile file : files) {
      if (filter != null && !filter.accept(file)) continue;
      map.put(file.getName(), file);
    }
    return map;
  }

  public static void assertDirectoriesEqual(VirtualFile dirAfter, VirtualFile dirBefore) throws IOException {
    assertDirectoriesEqual(dirAfter, dirBefore, null);
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  public static void assertDirectoriesEqual(VirtualFile dirAfter, VirtualFile dirBefore, @Nullable VirtualFileFilter fileFilter) throws IOException {
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile[] childrenAfter = dirAfter.getChildren();

    if (dirAfter.isInLocalFileSystem() && dirAfter.getFileSystem() != TempFileSystem.getInstance()) {
      File[] ioAfter = new File(dirAfter.getPath()).listFiles();
      shallowCompare(childrenAfter, ioAfter);
    }

    VirtualFile[] childrenBefore = dirBefore.getChildren();
    if (dirBefore.isInLocalFileSystem() && dirBefore.getFileSystem() != TempFileSystem.getInstance()) {
      File[] ioBefore = new File(dirBefore.getPath()).listFiles();
      shallowCompare(childrenBefore, ioBefore);
    }

    HashMap<String, VirtualFile> mapAfter = buildNameToFileMap(childrenAfter, fileFilter);
    HashMap<String, VirtualFile> mapBefore = buildNameToFileMap(childrenBefore, fileFilter);

    Set<String> keySetAfter = mapAfter.keySet();
    Set<String> keySetBefore = mapBefore.keySet();
    Assert.assertEquals(dirAfter.getPath(), keySetAfter, keySetBefore);

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

  private static void shallowCompare(VirtualFile[] vfs, @Nullable File[] io) {
    List<String> vfsPaths = new ArrayList<>();
    for (VirtualFile file : vfs) {
      vfsPaths.add(file.getPath());
    }

    List<String> ioPaths = new ArrayList<>();
    if (io != null) {
      for (File file : io) {
        ioPaths.add(file.getPath().replace(File.separatorChar, '/'));
      }
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
      assertJarFilesEqual(VfsUtilCore.virtualToIoFile(fileAfter), VfsUtilCore.virtualToIoFile(fileBefore));
    }
    catch (IOException e) {
      FileDocumentManager manager = FileDocumentManager.getInstance();

      Document docBefore = manager.getDocument(fileBefore);
      boolean canLoadBeforeText = !fileBefore.getFileType().isBinary() || fileBefore.getFileType() == FileTypes.UNKNOWN;
      String textB = docBefore != null
                     ? docBefore.getText()
                     : !canLoadBeforeText
                       ? null
                       : LoadTextUtil.getTextByBinaryPresentation(fileBefore.contentsToByteArray(false), fileBefore).toString();

      Document docAfter = manager.getDocument(fileAfter);
      boolean canLoadAfterText = !fileBefore.getFileType().isBinary() || fileBefore.getFileType() == FileTypes.UNKNOWN;
      String textA = docAfter != null
                     ? docAfter.getText()
                     : !canLoadAfterText
                       ? null
                       : LoadTextUtil.getTextByBinaryPresentation(fileAfter.contentsToByteArray(false), fileAfter).toString();

      if (textA != null && textB != null) {
        if (!StringUtil.equals(textA, textB)) {
          throw new FileComparisonFailure("Text mismatch in file " + fileBefore.getName(), textA, textB, fileAfter.getPath());
        }
      }
      else {
        Assert.assertArrayEquals(fileAfter.getPath(), fileAfter.contentsToByteArray(), fileBefore.contentsToByteArray());
      }
    }
  }

  public static void assertJarFilesEqual(File file1, File file2) throws IOException {
    final File tempDirectory1;
    final File tempDirectory2;

    try (JarFile jarFile1 = new JarFile(file1)) {
      try (JarFile jarFile2 = new JarFile(file2)) {
        tempDirectory1 = PlatformTestCase.createTempDir("tmp1");
        tempDirectory2 = PlatformTestCase.createTempDir("tmp2");
        ZipUtil.extract(jarFile1, tempDirectory1, null);
        ZipUtil.extract(jarFile2, tempDirectory2, null);
      }
    }

    final VirtualFile dirAfter = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory1);
    Assert.assertNotNull(tempDirectory1.toString(), dirAfter);
    final VirtualFile dirBefore = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory2);
    Assert.assertNotNull(tempDirectory2.toString(), dirBefore);
    getApplication().runWriteAction(() -> {
      dirAfter.refresh(false, true);
      dirBefore.refresh(false, true);
    });
    assertDirectoriesEqual(dirAfter, dirBefore);
  }

  /**
   * @deprecated Use com.intellij.testFramework.assertions.Assertions.assertThat().isEqualTo()
   */
  @SuppressWarnings("unused")
  @Deprecated
  public static void assertElementsEqual(final Element expected, final Element actual) {
    if (!JDOMUtil.areElementsEqual(expected, actual)) {
      Assert.assertEquals(JDOMUtil.writeElement(expected), JDOMUtil.writeElement(actual));
    }
  }

  /**
   * @deprecated Use com.intellij.testFramework.assertions.Assertions.assertThat().isEqualTo()
   */
  @SuppressWarnings("unused")
  @Deprecated
  public static void assertElementEquals(final String expected, final Element actual) {
    try {
      assertElementsEqual(JdomKt.loadElement(expected), actual);
    }
    catch (IOException | JDOMException e) {
      throw new AssertionError(e);
    }
  }

  public static String getCommunityPath() {
    final String homePath = PathManager.getHomePath();
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
    Assert.assertNotNull(t);
    return t;
  }

  @NotNull
  public static String loadFileText(@NotNull String fileName) throws IOException {
    return StringUtil.convertLineSeparators(FileUtil.loadFile(new File(fileName)));
  }

  public static void tryGcSoftlyReachableObjects() {
    GCUtil.tryGcSoftlyReachableObjects();
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
      Assert.assertEquals(output.getStdout(), 0, output.getExitCode());
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
        ExceptionUtil.rethrowAllAsUnchecked(e);
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


  public static <T> void assertComparisonContractNotViolated(@NotNull List<T> values,
                                                             @NotNull Comparator<T> comparator,
                                                             @NotNull Equality<T> equality) {
    for (int i1 = 0; i1 < values.size(); i1++) {
      for (int i2 = i1; i2 < values.size(); i2++) {
        T value1 = values.get(i1);
        T value2 = values.get(i2);

        int result12 = comparator.compare(value1, value2);
        int result21 = comparator.compare(value2, value1);
        if (equality.equals(value1, value2)) {
          Assert.assertEquals(String.format("Equal, but not 0: '%s' - '%s'", value1, value2), 0, result12);
          Assert.assertEquals(String.format("Equal, but not 0: '%s' - '%s'", value2, value1), 0, result21);
        }
        else {
          if (result12 == 0) Assert.fail(String.format("Not equal, but 0: '%s' - '%s'", value1, value2));
          if (result21 == 0) Assert.fail(String.format("Not equal, but 0: '%s' - '%s'", value2, value1));
          if (Integer.signum(result12) == Integer.signum(result21)) {
            Assert.fail(String.format("Not symmetrical: '%s' - '%s'", value1, value2));
          }
        }

        for (int i3 = i2; i3 < values.size(); i3++) {
          T value3 = values.get(i3);

          int result23 = comparator.compare(value2, value3);
          int result31 = comparator.compare(value3, value1);

          if (!isTransitive(result12, result23, result31)) {
            Assert.fail(String.format("Not transitive: '%s' - '%s' - '%s'", value1, value2, value3));
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