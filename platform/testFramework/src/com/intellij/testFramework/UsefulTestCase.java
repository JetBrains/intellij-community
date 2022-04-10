// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.DocumentCommitProcessor;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.junit.Assume.assumeTrue;

/**
 * This class is compatible with both JUnit 3 and JUnit 4. To use JUnit 4, just annotate your test subclass
 * with @RunWith(JUnit4.class) or any other (like Parametrized.class), and you are all set.
 *
 * Don't annotate the JUnit 3 setUp()/tearDown() methods as @Before/@After, and don't call them from other @Before/@After methods.
 * Also don't define @Rule's calling runBare(), just subclassing this class (directly or indirectly) is enough. <p/>
 *
 * The execution order is the following:
 * <pre{@code
 *
 *   - (JUnit 4 only) #checkShouldRunTest(Description) that can be used to ignore tests with meaningful message
 *   - #shouldRunTest() is also called (both JUnit 3 and JUnit 4)
 *
 *   - #setUp(), usually overridden so that it initializes classes in order from base to specific
 *
 *       - (JUnit 4 only) any @Rule fields, then @Rule methods
 *       - (JUnit 4 only) any @Before methods
 *
 *           - the testXxx() method (JUnit 3), or the @Test method (JUnit 4)
 *
 *       - (JUnit 4 only) any @After methods
 *       - (JUnit 4 only) any @Rule methods, then @Rule fields, cleanup
 *
 *   - #tearDown(), usually overridden in the reverse order: from specific to base
 * } </pre>
 *
 * Note that @Rule, @Before and @After methods execute within the same context/thread as the @Test method,
 * which may differ from how setUp()/tearDown() are executed.
 */
public abstract class UsefulTestCase extends TestCase {
  public static final boolean IS_UNDER_TEAMCITY = System.getenv("TEAMCITY_VERSION") != null;
  @ApiStatus.Internal
  public static final boolean IS_UNDER_SAFE_PUSH = IS_UNDER_TEAMCITY && "true".equals(System.getenv("SAFE_PUSH"));
  public static final String TEMP_DIR_MARKER = "unitTest_";
  public static final boolean OVERWRITE_TESTDATA = Boolean.getBoolean("idea.tests.overwrite.data");

  private static final String ORIGINAL_TEMP_DIR = FileUtilRt.getTempDirectory();

  private static final ObjectIntMap<String> TOTAL_SETUP_COST_MILLIS = new ObjectIntHashMap<>();
  private static final ObjectIntMap<String> TOTAL_SETUP_COUNT = new ObjectIntHashMap<>();
  private static final ObjectIntMap<String> TOTAL_TEARDOWN_COST_MILLIS = new ObjectIntHashMap<>();
  private static final ObjectIntMap<String> TOTAL_TEARDOWN_COUNT = new ObjectIntHashMap<>();

  protected static final Logger LOG = Logger.getInstance(UsefulTestCase.class);

  private @Nullable Disposable myTestRootDisposable;

  private @Nullable List<Path> myPathsToKeep;
  private @Nullable Path myTempDir;

  private static final CodeInsightSettings defaultSettings = new CodeInsightSettings();

  static {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    Logger.setFactory(TestLoggerFactory.class);

    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  /**
   * This @Rule ensures that JUnit4 tests defined in subclasses annotated with @RunWith are executed
   * in the exactly the same context as if they would have been executed were they plain old JUnit3 tests.
   * This includes calling all the necessary setUp()/tearDown() together with any other customization defined by the
   * platform subclasses like {@link LightPlatformTestCase} or {@link HeavyPlatformTestCase}: running on EDT,
   * in a write action, wrapped in a command, etc.
   *
   * It's executed around any other rules and @Before/@After methods defined in subclasses.
   * Subclasses may change this by using {@link #asOuterRule}.
   */
  @Rule
  public @NotNull TestRule runBareTestRule = (base, description) -> new Statement() {
    @Override
    public void evaluate() throws Throwable {
      String name = description.getMethodName();
      name = StringUtil.notNullize(StringUtil.substringBefore(name, "["), name);
      setName(name);
      checkShouldRunTest(description);
      runBare(base::evaluate);
    }
  };

  /**
   * Use to make the specified rule applied around the base rule.
   * This may be useful in case you need to access the rule from {@link #setUp()}.
   *
   * NB. Do not annotate the field that you assign the result of an asOuterRule() call to, as @Rule.
   *     Otherwise the rule is going to be applied twice.
   */
  protected @NotNull <R extends TestRule> R asOuterRule(@NotNull R rule) {
    runBareTestRule = RuleChain.outerRule(rule).around(runBareTestRule);
    return rule;
  }

  /**
   * Pass here the exception you want to be thrown first
   * E.g.<pre>
   * {@code
   *   void tearDown() {
   *     try {
   *       doTearDowns();
   *     }
   *     catch(Exception e) {
   *       addSuppressedException(e);
   *     }
   *     finally {
   *       super.tearDown();
   *     }
   *   }
   * }
   * </pre>
   */
  protected void addSuppressedException(@NotNull Throwable e) {
    List<Throwable> list = mySuppressedExceptions;
    if (list == null) {
      mySuppressedExceptions = list = new SmartList<>();
    }
    list.add(e);
  }
  private List<Throwable> mySuppressedExceptions;

  public UsefulTestCase() {
  }

  public UsefulTestCase(@NotNull String name) {
    super(name);
  }

  protected void checkShouldRunTest(@NotNull Description description) throws AssumptionViolatedException {
    assumeTrue("skipped: shouldRunTest() returned false", shouldRunTest());
  }

  protected boolean shouldContainTempFiles() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setupTempDir();

    boolean isStressTest = isStressTest();
    ApplicationManagerEx.setInStressTest(isStressTest);
    if (isPerformanceTest()) {
      Timings.getStatistics();
    }

    // turn off Disposer debugging for performance tests
    Disposer.setDebugMode(!isStressTest);

    if (isIconRequired()) {
      // ensure that IconLoader will use dummy empty icon
      try {
        IconManager.activate(new CoreIconManager());
      }
      catch (Exception e) {
        throw e;
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }

  // some brilliant tests overrides setup and change setup flow in an alien way - quite unsafe and error prone to fix for now,
  // so, expose method for such a brilliant test classes
  protected final void setupTempDir() throws IOException {
    if (myTempDir == null && shouldContainTempFiles()) {
      myTempDir = createGlobalTempDirectory();
    }
  }

  @ApiStatus.Internal
  @NotNull Path createGlobalTempDirectory() throws IOException {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    String testName = null;
    if (policy != null) {
      testName = policy.getPerTestTempDirName();
    }
    if (testName == null) {
      testName = FileUtil.sanitizeFileName(getTestName(true));
    }

    Path result = TemporaryDirectory.generateTemporaryPath(TEMP_DIR_MARKER + testName);
    Files.createDirectories(result);
    FileUtil.resetCanonicalTempPathCache(result.toString());
    return result;
  }

  @ApiStatus.Internal
  void removeGlobalTempDirectory(@NotNull Path dir) throws Exception {
    if (myPathsToKeep == null || myPathsToKeep.isEmpty()) {
      PathKt.delete(dir);
    }
    else {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
        for (Path file : directoryStream) {
          if (!shouldKeepTmpFile(file)) {
            FileUtil.delete(file);
          }
        }
      }
      catch (NoSuchFileException ignore) {
      }
    }
  }

  protected boolean isIconRequired() {
    return false;
  }

  @Override
  protected void tearDown() throws Exception {
    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    new RunAll(
      () -> {
        if (isIconRequired()) {
          IconManager.deactivate();
          IconLoader.clearCacheInTests();
        }
      },
      () -> disposeRootDisposable(),
      () -> cleanupSwingDataStructures(),
      () -> cleanupDeleteOnExitHookList(),
      () -> Disposer.setDebugMode(true),
      () -> {
        if (myTempDir != null) {
          FileUtil.resetCanonicalTempPathCache(ORIGINAL_TEMP_DIR);
          try {
            removeGlobalTempDirectory(myTempDir);
          }
          catch (Throwable e) {
            printThreadDump();
            throw e;
          }
        }
      },
      () -> waitForAppLeakingThreads(10, TimeUnit.SECONDS),
      () -> clearFields(this)
    ).run(mySuppressedExceptions);
  }

  protected final void disposeRootDisposable() {
    Disposer.dispose(getTestRootDisposable());
  }

  protected void addTmpFileToKeep(@NotNull Path file) {
    if (myPathsToKeep == null) {
      myPathsToKeep = new ArrayList<>();
    }
    myPathsToKeep.add(file.toAbsolutePath());
  }

  private boolean shouldKeepTmpFile(@NotNull Path file) {
    if (myPathsToKeep == null || myPathsToKeep.isEmpty()) {
      return false;
    }
    for (Path pathToKeep : myPathsToKeep) {
      if (file.equals(pathToKeep)) {
        return true;
      }
    }
    return false;
  }

  private static final Set<String> DELETE_ON_EXIT_HOOK_DOT_FILES;
  private static final Class<?> DELETE_ON_EXIT_HOOK_CLASS;
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
  private static void cleanupDeleteOnExitHookList() {
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
    Map<?, ?> componentKeyStrokeMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "componentKeyStrokeMap");
    componentKeyStrokeMap.clear();
    Map<?, ?> containerMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "containerMap");
    containerMap.clear();
  }

  static void doCheckForSettingsDamage(@NotNull CodeStyleSettings oldCodeStyleSettings, @NotNull CodeStyleSettings currentCodeStyleSettings) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    new RunAll(
      () -> {
        try {
          checkCodeInsightSettingsNotOverwritten(settings);
        }
        catch (AssertionError error) {
          restoreCodeInsightSettingsToAvoidInducedErrors(settings);
          throw error;
        }
      },
      () -> {
        currentCodeStyleSettings.getIndentOptions(FileTypeManager.getInstance().getStdFileType("JAVA"));
        try {
          checkCodeStyleSettingsEqual(oldCodeStyleSettings, currentCodeStyleSettings);
        }
        finally {
          currentCodeStyleSettings.clearCodeStyleSettings();
        }
      }
    ).run();
  }

  private static void restoreCodeInsightSettingsToAvoidInducedErrors(@NotNull CodeInsightSettings settings) {
    CodeInsightSettings clean = new CodeInsightSettings();
    for (Field field : clean.getClass().getFields()) {
      try {
        ReflectionUtil.copyFieldValue(clean, settings, field);
      }
      catch (Exception ignored) {
      }
    }
  }

  /**
   * Test root disposable is used for add an activity on test {@link #tearDown()}
   *
   * @see #disposeOnTearDown(Disposable)
   * @see #tearDown()
   */
  public @NotNull Disposable getTestRootDisposable() {
    Disposable disposable = myTestRootDisposable;
    if (disposable == null) {
      myTestRootDisposable = disposable = new TestDisposable();
    }
    return disposable;
  }

  /**
   * @deprecated not JUnit4-friendly; to override the way tests are executed use {@link #runTestRunnable} instead
   */
  @Override
  @Deprecated
  protected final void runTest() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Use runTestRunnable() to override the way tests are executed");
  }

  protected boolean shouldRunTest() {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null && !policy.canRun(getClass())) {
      return false;
    }
    return TestFrameworkUtil.canRunTest(getClass());
  }

  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    testRunnable.run();
  }

  /**
   * This reflects the way the default {@link TestCase#runBare} works, with few notable exceptions:
   * <ul>
   *   <li/> {@link #tearDown} is called even if {@link #setUp} has failed;
   *   <li/> exceptions from tearDown() don't shadow those from the main test method, but are rather linked as suppressed;
   *   <li/> it allows to customise the way the methods are invoked through {@link #runTestRunnable},
   *         {@link #invokeSetUp} and {@link #invokeTearDown}, for example, to make them execute on a different thread.
   * </ul>
   */
  protected void defaultRunBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try (AutoCloseable ignored = this::invokeTearDown) {
      invokeSetUp();

      runTestRunnable(testRunnable);
    }
  }

  protected final void invokeSetUp() throws Exception {
    long setupStart = System.nanoTime();
    setUp();
    long setupCost = (System.nanoTime() - setupStart) / 1000000;
    logPerClassCost((int)setupCost, TOTAL_SETUP_COST_MILLIS, TOTAL_SETUP_COUNT);
  }

  protected void invokeTearDown() throws Exception {
    long teardownStart = System.nanoTime();
    tearDown();
    long teardownCost = (System.nanoTime() - teardownStart) / 1000000;
    logPerClassCost((int)teardownCost, TOTAL_TEARDOWN_COST_MILLIS, TOTAL_TEARDOWN_COUNT);
  }

  /**
   * Logs the setup cost grouped by test fixture class (superclass of the current test class).
   *
   * @param cost setup cost in milliseconds
   */
  private void logPerClassCost(int cost,
                               @NotNull ObjectIntMap<String> costMap,
                               @NotNull ObjectIntMap<String> countMap) {
    String name = getClass().getSuperclass().getName();
    int storedCost = costMap.get(name);
    costMap.put(name, (storedCost == -1 ? 0 : storedCost)+cost);
    int storedCount = countMap.get(name);
    countMap.put(name, storedCount == -1 ? 1 : storedCount+1);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void logSetupTeardownCosts() {
    System.out.println("Setup costs");
    long totalSetup = 0;
    for (ObjectIntMap.Entry<String> entry : TOTAL_SETUP_COST_MILLIS.entries()) {
      String name = entry.getKey();
      int cost = entry.getValue();
      long count = TOTAL_SETUP_COUNT.get(name);
      System.out.printf("  %s: %d ms for %d executions%n", name, cost, count);
      totalSetup += cost;
    }
    System.out.println("Teardown costs");
    long totalTeardown = 0;
    for (ObjectIntMap.Entry<String> entry : TOTAL_TEARDOWN_COST_MILLIS.entries()) {
      String name = entry.getKey();
      int cost = entry.getValue();
      long count = TOTAL_TEARDOWN_COUNT.get(name);
      System.out.printf("  %s: %d ms for %d executions%n", name, cost, count);
      totalTeardown += cost;
    }
    System.out.printf("Total overhead: setup %d ms, teardown %d ms%n", totalSetup, totalTeardown);
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalSetupMs' value='%d']%n", totalSetup);
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalTeardownMs' value='%d']%n", totalTeardown);
  }

  @Override
  public final void runBare() throws Throwable {
    if (!shouldRunTest()) {
      return;
    }
    runBare(super::runTest);
  }

  protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    final ThrowableRunnable<Throwable> wrappedRunnable = wrapTestRunnable(testRunnable);

    if (runInDispatchThread()) {
      TestApplicationManagerKt.replaceIdeEventQueueSafely();
      EdtTestUtil.runInEdtAndWait(() -> defaultRunBare(wrappedRunnable));
    }
    else {
      defaultRunBare(wrappedRunnable);
    }
  }

  protected @NotNull ThrowableRunnable<Throwable> wrapTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
    var testDescription = Description.createTestDescription(getClass(), getName());
    return () -> {
      boolean success = false;
      TestLoggerFactory.onTestStarted();
      try {
        testRunnable.run();
        success = true;
      }
      catch (AssumptionViolatedException e) {
        success = true;
        throw e;
      }
      catch (Throwable t) {
        TestLoggerFactory.logTestFailure(t);
        throw t;
      }
      finally {
        TestLoggerFactory.onTestFinished(success, testDescription);
      }
    };
  }

  protected boolean runInDispatchThread() {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null) {
      return policy.runInDispatchThread();
    }
    return true;
  }

  /**
   * If you want a more shorter name than runInEdtAndWait.
   */
  protected static <T extends Throwable> void edt(@NotNull ThrowableRunnable<T> runnable) throws T {
    EdtTestUtil.runInEdtAndWait(runnable);
  }

  public static @NotNull String toString(@NotNull Iterable<?> collection) {
    if (!collection.iterator().hasNext()) {
      return "<empty>";
    }

    StringBuilder builder = new StringBuilder();
    for (Object o : collection) {
      if (o instanceof Set) {
        builder.append(new TreeSet<>((Set<?>)o));
      }
      else {
        builder.append(o);
      }
      builder.append('\n');
    }
    return builder.toString();
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(T @NotNull [] actual, T @NotNull ... expected) {
    assertOrderedEquals(Arrays.asList(actual), expected);
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(@NotNull Iterable<? extends T> actual, T @NotNull ... expected) {
    assertOrderedEquals("", actual, expected);
  }

  public static void assertOrderedEquals(byte @NotNull [] actual, byte @NotNull [] expected) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < actual.length; i++) {
      byte a = actual[i];
      byte e = expected[i];
      assertEquals("not equals at index: "+i, e, a);
    }
  }

  public static void assertOrderedEquals(int @NotNull [] actual, int @NotNull [] expected) {
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
  public static <T> void assertOrderedEquals(@NotNull String errorMsg, @NotNull Iterable<? extends T> actual, T @NotNull ... expected) {
    assertOrderedEquals(errorMsg, actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(@NotNull Iterable<? extends T> actual, @NotNull Iterable<? extends T> expected) {
    assertOrderedEquals("", actual, expected);
  }

  public static <T> void assertOrderedEquals(@NotNull String errorMsg,
                                             @NotNull Iterable<? extends T> actual,
                                             @NotNull Iterable<? extends T> expected) {
    assertOrderedEquals(errorMsg, actual, expected, Objects::equals);
  }

  public static <T> void assertOrderedEquals(@NotNull String errorMsg,
                                             @NotNull Iterable<? extends T> actual,
                                             @NotNull Iterable<? extends T> expected,
                                             @NotNull BiPredicate<? super T, ? super T> predicate) {
    if (!equals(actual, expected, predicate)) {
      String expectedString = toString(expected);
      String actualString = toString(actual);
      Assert.assertEquals(errorMsg, expectedString, actualString);
      Assert.fail("Warning! 'toString' does not reflect the difference.\nExpected: " + expectedString + "\nActual: " + actualString);
    }
  }

  private static <T> boolean equals(@NotNull Iterable<? extends T> a1,
                                    @NotNull Iterable<? extends T> a2,
                                    @NotNull BiPredicate<? super T, ? super T> predicate) {
    Iterator<? extends T> it1 = a1.iterator();
    Iterator<? extends T> it2 = a2.iterator();
    while (it1.hasNext() || it2.hasNext()) {
      if (!it1.hasNext() || !it2.hasNext() || !predicate.test(it1.next(), it2.next())) {
        return false;
      }
    }
    return true;
  }

  @SafeVarargs
  public static <T> void assertOrderedCollection(T @NotNull [] collection, Consumer<? super T> @NotNull ... checkers) {
    assertOrderedCollection(Arrays.asList(collection), checkers);
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  @SafeVarargs
  public static <T> void assertSameElements(T @NotNull [] actual, T @NotNull ... expected) {
    assertSameElements(Arrays.asList(actual), expected);
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  @SafeVarargs
  public static <T> void assertSameElements(@NotNull Collection<? extends T> actual, T @NotNull ... expected) {
    assertSameElements(actual, Arrays.asList(expected));
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  public static <T> void assertSameElements(@NotNull Collection<? extends T> actual, @NotNull Collection<? extends T> expected) {
    assertSameElements("", actual, expected);
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  public static <T> void assertSameElements(@NotNull String message, @NotNull Collection<? extends T> actual, @NotNull Collection<? extends T> expected) {
    if (actual.size() != expected.size() || !new LinkedHashSet<>(expected).equals(new LinkedHashSet<T>(actual))) {
      Assert.assertEquals(message, new LinkedHashSet<>(expected), new LinkedHashSet<T>(actual));
    }
  }

  @SafeVarargs
  public static <T> void assertContainsOrdered(@NotNull Collection<? extends T> collection, T @NotNull ... expected) {
    assertContainsOrdered(collection, Arrays.asList(expected));
  }

  public static <T> void assertContainsOrdered(@NotNull Collection<? extends T> collection, @NotNull Collection<? extends T> expected) {
    PeekableIterator<T> expectedIt = new PeekableIteratorWrapper<>(expected.iterator());
    PeekableIterator<T> actualIt = new PeekableIteratorWrapper<>(collection.iterator());

    while (actualIt.hasNext() && expectedIt.hasNext()) {
      T expectedElem = expectedIt.peek();
      T actualElem = actualIt.peek();
      if (expectedElem.equals(actualElem)) {
        expectedIt.next();
      }
      actualIt.next();
    }
    if (expectedIt.hasNext()) {
      throw new ComparisonFailure("", toString(expected), toString(collection));
    }
  }

  @SafeVarargs
  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, T @NotNull ... expected) {
    assertContainsElements(collection, Arrays.asList(expected));
  }

  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, @NotNull Collection<? extends T> expected) {
    List<T> copy = new ArrayList<>(collection);
    copy.retainAll(expected);
    assertSameElements(toString(collection), copy, expected);
  }

  public static @NotNull String toString(Object @NotNull [] collection, @NotNull String separator) {
    return toString(Arrays.asList(collection), separator);
  }

  @SafeVarargs
  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, T @NotNull ... notExpected) {
    assertDoesntContain(collection, Arrays.asList(notExpected));
  }

  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, @NotNull Collection<? extends T> notExpected) {
    List<T> expected = new ArrayList<>(collection);
    expected.removeAll(notExpected);
    assertSameElements(collection, expected);
  }

  public static @NotNull String toString(@NotNull Collection<?> collection, @NotNull String separator) {
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
  public static <T> void assertOrderedCollection(@NotNull Collection<? extends T> collection, Consumer<? super T> @NotNull ... checkers) {
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
  public static <T> void assertUnorderedCollection(T @NotNull [] collection, Consumer<? super T> @NotNull ... checkers) {
    assertUnorderedCollection(Arrays.asList(collection), checkers);
  }

  @SafeVarargs
  public static <T> void assertUnorderedCollection(@NotNull Collection<? extends T> collection, Consumer<? super T> @NotNull ... checkers) {
    if (collection.size() != checkers.length) {
      Assert.fail(toString(collection));
    }
    Set<Consumer<? super T>> checkerSet = ContainerUtil.set(checkers);
    int i = 0;
    Throwable lastError = null;
    for (final T actual : collection) {
      boolean flag = true;
      for (final Consumer<? super T> condition : checkerSet) {
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

  private static <T> Throwable accepts(@NotNull Consumer<? super T> condition, final T actual) {
    try {
      condition.consume(actual);
      return null;
    }
    catch (Throwable e) {
      return e;
    }
  }

  @Contract("null, _ -> fail")
  public static @NotNull <T> T assertInstanceOf(Object o, @NotNull Class<T> aClass) {
    Assert.assertNotNull("Expected instance of: " + aClass.getName() + " actual: " + null, o);
    Assert.assertTrue("Expected instance of: " + aClass.getName() + " actual: " + o.getClass().getName(), aClass.isInstance(o));
    @SuppressWarnings("unchecked") T t = (T)o;
    return t;
  }

  public static <T> T assertOneElement(@NotNull Collection<? extends T> collection) {
    Assert.assertEquals(collection.toString(), 1, collection.size());
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T @NotNull [] ts) {
    Assert.assertEquals(Arrays.toString(ts), 1, ts.length);
    return ts[0];
  }

  @SafeVarargs
  public static <T> void assertOneOf(T value, T @NotNull ... values) {
    for (T v : values) {
      if (Objects.equals(value, v)) {
        return;
      }
    }
    Assert.fail(value + " should be equal to one of " + Arrays.toString(values));
  }

  public static void printThreadDump() {
    PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
  }

  public static void assertEmpty(Object @NotNull [] array) {
    assertOrderedEquals(array);
  }

  public static void assertNotEmpty(final Collection<?> collection) {
    assertNotNull(collection);
    assertFalse(collection.isEmpty());
  }

  public static void assertEmpty(@NotNull Collection<?> collection) {
    assertEmpty(collection.toString(), collection);
  }

  public static void assertNullOrEmpty(@Nullable Collection<?> collection) {
    if (collection == null) return;
    assertEmpty("", collection);
  }

  public static void assertEmpty(final String s) {
    assertTrue(s, StringUtil.isEmpty(s));
  }

  public static <T> void assertEmpty(@NotNull String errorMsg, @NotNull Collection<? extends T> collection) {
    assertOrderedEquals(errorMsg, collection, Collections.emptyList());
  }

  public static void assertSize(int expectedSize, Object @NotNull [] array) {
    if (array.length != expectedSize) {
      assertEquals(toString(Arrays.asList(array)), expectedSize, array.length);
    }
  }

  public static void assertSize(int expectedSize, @NotNull Collection<?> c) {
    if (c.size() != expectedSize) {
      assertEquals(toString(c), expectedSize, c.size());
    }
  }

  protected @NotNull <T extends Disposable> T disposeOnTearDown(@NotNull T disposable) {
    Disposer.register(getTestRootDisposable(), disposable);
    return disposable;
  }

  public static void assertSameLines(@NotNull String expected, @NotNull String actual) {
    assertSameLines(null, expected, actual);
  }

  public static void assertSameLines(@Nullable String message, @NotNull String expected, @NotNull String actual) {
    String expectedText = StringUtil.convertLineSeparators(expected.trim());
    String actualText = StringUtil.convertLineSeparators(actual.trim());
    Assert.assertEquals(message, expectedText, actualText);
  }

  public static void assertExists(@NotNull File file){
    assertTrue("File should exist " + file, file.exists());
  }

  public static void assertDoesntExist(@NotNull File file){
    assertFalse("File should not exist " + file, file.exists());
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return getTestName(getName(), lowercaseFirstLetter);
  }

  public static @NotNull String getTestName(@Nullable String name, boolean lowercaseFirstLetter) {
    return name == null ? "" : PlatformTestUtil.getTestName(name, lowercaseFirstLetter);
  }

  protected @NotNull String getTestDirectoryName() {
    final String testName = getTestName(true);
    return testName.replaceAll("_.*", "");
  }

  public static void assertSameLinesWithFile(@NotNull String filePath, @NotNull String actualText) {
    assertSameLinesWithFile(filePath, actualText, true);
  }

  public static void assertSameLinesWithFile(@NotNull String filePath,
                                             @NotNull String actualText,
                                             @NotNull Supplier<String> messageProducer) {
    assertSameLinesWithFile(filePath, actualText, true, messageProducer);
  }

  public static void assertSameLinesWithFile(@NotNull String filePath, @NotNull String actualText, boolean trimBeforeComparing) {
    assertSameLinesWithFile(filePath, actualText, trimBeforeComparing, null);
  }

  public static void assertSameLinesWithFile(@NotNull String filePath,
                                             @NotNull String actualText,
                                             boolean trimBeforeComparing,
                                             @Nullable Supplier<String> messageProducer) {
    String fileText;
    try {
      if (OVERWRITE_TESTDATA) {
        VfsTestUtil.overwriteTestData(filePath, actualText, trimBeforeComparing);
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("File " + filePath + " created.");
      }
      fileText = FileUtil.loadFile(new File(filePath), StandardCharsets.UTF_8);
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
    if (!Objects.equals(expected, actual)) {
      throw new FileComparisonFailure(messageProducer == null ? null : messageProducer.get(), expected, actual, filePath);
    }
  }

  protected static void clearFields(@NotNull Object test) throws IllegalAccessException {
    Class<?> aClass = test.getClass();
    while (aClass != null) {
      clearDeclaredFields(test, aClass);
      aClass = aClass.getSuperclass();
    }
  }

  public static void clearDeclaredFields(@NotNull Object test, @NotNull Class<?> aClass) throws IllegalAccessException {
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

  private static void checkCodeStyleSettingsEqual(@NotNull CodeStyleSettings expected, @NotNull CodeStyleSettings settings) {
    if (!expected.equals(settings)) {
      Element oldS = new Element("temp");
      expected.writeExternal(oldS);
      Element newS = new Element("temp");
      settings.writeExternal(newS);

      String newString = JDOMUtil.writeElement(newS);
      String oldString = JDOMUtil.writeElement(oldS);
      Assert.assertEquals("Code style settings damaged", oldString, newString);
    }
  }

  private static void checkCodeInsightSettingsNotOverwritten(@NotNull CodeInsightSettings settings) {
    if (!settings.equals(defaultSettings)) {
      Element newS = new Element("temp");
      settings.writeExternal(newS);
      Element oldS = new Element("temp");
      defaultSettings.writeExternal(oldS);
      String DEFAULT_SETTINGS_EXTERNALIZED = JDOMUtil.writeElement(oldS);
      Assert.assertEquals("Code insight settings damaged", DEFAULT_SETTINGS_EXTERNALIZED, JDOMUtil.writeElement(newS));
    }
  }

  /**
   * @return true for a test which performs A LOT of computations to test resources consumption, not correctness.
   * Such test should avoid performing expensive consistency checks, e.g. data structure consistency complex validations.
   * If you want your test to be treated as "Performance", mention "Performance" word in its class/method name.
   * For example: {@code public void testHighlightingPerformance()}
   */
  public final boolean isPerformanceTest() {
    String testName = getName();
    String className = getClass().getSimpleName();
    return TestFrameworkUtil.isPerformanceTest(testName, className);
  }

  /**
   * @return true for a test which performs A LOT of computations, but which does care about correctness of operations it performs.
   * Such test should typically avoid performing expensive checks, e.g. data structure consistency complex validations.
   * If you want your test to be treated as "Stress", please mention one of these words in its name: "Stress", "Slow".
   * For example: {@code public void testStressPSIFromDifferentThreads()}
   */
  public final boolean isStressTest() {
    return isStressTest(getName(), getClass().getName());
  }

  private static boolean isStressTest(String testName, String className) {
    return TestFrameworkUtil.isPerformanceTest(testName, className) ||
           containsStressWords(testName) ||
           containsStressWords(className);
  }

  private static boolean containsStressWords(@Nullable String name) {
    return name != null && (name.contains("Stress") || name.contains("Slow"));
  }

  public static void doPostponedFormatting(@NotNull Project project) {
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    });
  }

  /**
   * Checks that the code block throws an exception of the specified class.
   *
   * @param exceptionClass   Expected exception type
   * @param runnable         Block annotated with some exception type
   */
  public static void assertThrows(@NotNull Class<? extends Throwable> exceptionClass, @NotNull ThrowableRunnable<?> runnable) {
    assertThrows(exceptionClass, null, runnable);
  }

  /**
   * Checks that the code block throws an exception of the specified class with expected error msg.
   * If expected error message is null it will not be checked.
   *
   * @param exceptionClass   Expected exception type
   * @param expectedErrorMsgPart expected error message, of any
   * @param runnable         Block annotated with some exception type
   */
  public static void assertThrows(@NotNull Class<? extends Throwable> exceptionClass,
                                  @Nullable String expectedErrorMsgPart,
                                  @NotNull ThrowableRunnable<?> runnable) {
    boolean wasThrown = false;
    try {
      runnable.run();
    }
    catch (Throwable e) {
      Throwable cause = e;
      while (cause instanceof TestLogger.TestLoggerAssertionError && cause.getCause() != null) {
        cause = cause.getCause();
      }

      wasThrown = true;
      if (!exceptionClass.isInstance(cause)) {
        throw new AssertionError("Expected instance of: " + exceptionClass + " actual: " + cause.getClass(), cause);
      }

      if (expectedErrorMsgPart != null) {
        assertTrue(cause.getMessage(), cause.getMessage().contains(expectedErrorMsgPart));
      }
    }
    finally {
      if (!wasThrown) {
        fail(exceptionClass + " must be thrown.");
      }
    }
  }

  protected static <T extends Throwable> void assertNoException(@NotNull Class<? extends Throwable> exceptionClass, @NotNull ThrowableRunnable<T> runnable) throws T {
    try {
      runnable.run();
    }
    catch (Throwable e) {
      Throwable cause = e;
      while (cause instanceof TestLogger.TestLoggerAssertionError && cause.getCause() != null) {
        cause = cause.getCause();
      }

      if (exceptionClass.equals(cause.getClass())) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println();
        //noinspection UseOfSystemOutOrSystemErr
        e.printStackTrace(System.out);

        fail("Exception isn't expected here. Exception message: " + cause.getMessage());
      }
      else {
        throw e;
      }
    }
  }

  protected void assertNoThrowable(@NotNull Runnable closure) {
    String throwableName = null;
    try {
      closure.run();
    }
    catch (Throwable thr) {
      throwableName = thr.getClass().getName();
    }
    assertNull(throwableName);
  }

  protected boolean annotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
    Class<?> aClass = getClass();
    String methodName = ObjectUtils.notNull(getName(), "");
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

  protected @NotNull String getHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  public static void refreshRecursively(@NotNull VirtualFile file) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        file.getChildren();
        return true;
      }
    });
    file.refresh(false, true);
  }

  public static VirtualFile refreshAndFindFile(final @NotNull File file) {
    return UIUtil.invokeAndWaitIfNeeded(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
  }

  public static void waitForAppLeakingThreads(long timeout, @NotNull TimeUnit timeUnit) throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        FileBasedIndex index = app.getServiceIfCreated(FileBasedIndex.class);
        if (index instanceof FileBasedIndexImpl) {
          ((FileBasedIndexImpl)index).getChangedFilesCollector().waitForVfsEventsExecuted(timeout, timeUnit);
        }

        DocumentCommitThread commitThread = (DocumentCommitThread)app.getServiceIfCreated(DocumentCommitProcessor.class);
        if (commitThread != null) {
          commitThread.waitForAllCommits(timeout, timeUnit);
        }
      }
    });
  }

  protected final class TestDisposable implements Disposable {
    private volatile boolean myDisposed;

    public TestDisposable() { }

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

  protected void setRegistryPropertyForTest(@NotNull String property, @NotNull String value) {
    Registry.get(property).setValue(value);
    Disposer.register(getTestRootDisposable(), () -> Registry.get(property).resetToDefault());
  }

  protected void allowAccessToDirsIfExists(@NotNull String @NotNull ... dirNames) {
    for (String dirName : dirNames) {
      final Path usrShareDir = Paths.get(dirName);
      if (Files.exists(usrShareDir)) {
        final String absolutePath = usrShareDir.toAbsolutePath().toString();
        LOG.debug(usrShareDir.toString(), " exists, adding to the list of allowed root: ", absolutePath);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), absolutePath);
      }
      else {
        LOG.debug(usrShareDir.toString(), " does not exists");
      }
    }
  }
}
