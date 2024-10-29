// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.testFramework.DumbModeTestUtils.EternalTaskShutdownToken;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.UnindexedFilesScanner;
import com.intellij.util.indexing.UnindexedFilesScannerExecutorImpl;
import junit.framework.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.internal.MethodSorter;
import org.junit.runner.Describable;
import org.junit.runner.Description;

import java.lang.reflect.*;

import static com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode.DUMB_EMPTY_INDEX;
import static junit.framework.TestSuite.warning;

/**
 * To run a test with needed {@link IndexingMode}, it's enough to make getIndexingMode return it and run the test with IDE's gutter action.
 * To run all dumb mode completion tests, check JavaDoc of
 * {@link com.intellij.java.codeInsight.completion.JavaCompletionTestSuite} or
 * {@link com.jetbrains.php.PhpDumbCompletionTest}
 */
public interface TestIndexingModeSupporter {
  enum IndexingMode {
    SMART {
      @Override
      public @NotNull ShutdownToken setUpTestInternal(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        return new ShutdownToken(null);
      }

      @Override
      public void ensureIndexingStatus(@NotNull Project project) {
        IndexingTestUtil.waitUntilIndexesAreReady(project);
      }
    }, DUMB_FULL_INDEX {
      @Override
      public @NotNull ShutdownToken setUpTestInternal(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        EternalTaskShutdownToken dumbTask = indexEverythingAndBecomeDumb(project);
        onlyAllowOwnTasks(project, testRootDisposable);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
        // we don't want "waiting-for-non-dumb-mode" to pause tasks submitted from ensureIndexingStatus
        UnindexedFilesScannerExecutorImpl.getInstance(project).overrideScanningWaitsForNonDumbMode(false);
        return new ShutdownToken(dumbTask);
      }

      @Override
      public void ensureIndexingStatus(@NotNull Project project) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          // if not invoked from EDT scanning will be queued asynchronously
          new UnindexedFilesScanner(project, INDEXING_REASON).queue();
        });
        IndexingTestUtil.waitUntilIndexesAreReady(project);
      }
    }, DUMB_RUNTIME_ONLY_INDEX {
      @Override
      public @NotNull ShutdownToken setUpTestInternal(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        EternalTaskShutdownToken dumbTask = becomeDumb(project);
        onlyAllowOwnTasks(project, testRootDisposable);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
        // we don't want "waiting-for-non-dumb-mode" to pause tasks submitted from ensureIndexingStatus
        UnindexedFilesScannerExecutorImpl.getInstance(project).overrideScanningWaitsForNonDumbMode(false);
        return new ShutdownToken(dumbTask);
      }
    }, DUMB_EMPTY_INDEX {
      @Override
      public @NotNull ShutdownToken setUpTestInternal(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        // indexing code does not expect that FileBasedIndex implementation changes during execution
        IndexingTestUtil.waitUntilIndexesAreReady(project);

        ServiceContainerUtil
          .replaceService(ApplicationManager.getApplication(), FileBasedIndex.class, new EmptyFileBasedIndex(), testRootDisposable);
        EternalTaskShutdownToken dumbTask = becomeDumb(project);
        onlyAllowOwnTasks(project, testRootDisposable);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
        return new ShutdownToken(dumbTask);
      }
    };

    private static final String INDEXING_REASON = "TestIndexingModeSupporter";

    private static void onlyAllowOwnTasks(@NotNull Project project, @NotNull Disposable testRootDisposable) {
      UnindexedFilesScannerExecutorImpl.getInstance(project)
        .setTaskFilterInTest(testRootDisposable, task -> Strings.areSameInstance(task.getIndexingReason(), INDEXING_REASON));
      ApplicationManager.getApplication().invokeAndWait(() -> {
        UnindexedFilesScannerExecutorImpl.getInstance(project).cancelAllTasksAndWait();
      });
    }

    public static final class ShutdownToken {
      private final @Nullable EternalTaskShutdownToken dumbTask;

      private ShutdownToken(@Nullable EternalTaskShutdownToken dumbTask) {
        this.dumbTask = dumbTask;
      }
    }

    protected abstract @NotNull ShutdownToken setUpTestInternal(@NotNull Project project, @NotNull Disposable testRootDisposable);

    public final @NotNull ShutdownToken setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
      ShutdownToken shutdownToken = setUpTestInternal(project, testRootDisposable);
      Disposer.register(testRootDisposable, new Disposable() {
        @Override
        public void dispose() {
          tearDownTest(project, shutdownToken);
        }
      });
      return shutdownToken;
    }

    public void tearDownTest(@Nullable Project project, @NotNull ShutdownToken token) {
      if (token.dumbTask != null) {
        DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token.dumbTask);
        if (project != null) {
          UnindexedFilesScannerExecutorImpl.getInstance(project).overrideScanningWaitsForNonDumbMode(null /* reset to default */);
        }
      }
    }

    public void ensureIndexingStatus(@NotNull Project project) {
    }

    private static EternalTaskShutdownToken becomeDumb(@NotNull Project project) {
      return DumbModeTestUtils.startEternalDumbModeTask(project);
    }

    private static EternalTaskShutdownToken indexEverythingAndBecomeDumb(@NotNull Project project) {
      new UnindexedFilesScanner(project, INDEXING_REASON).queue();
      IndexingTestUtil.waitUntilIndexesAreReady(project);
      return becomeDumb(project);
    }
  }

  void setIndexingMode(@NotNull IndexingMode mode);

  @NotNull IndexingMode getIndexingMode();

  static void addTest(@NotNull Class<? extends TestIndexingModeSupporter> aClass,
                      @NotNull TestIndexingModeSupporter.IndexingModeTestHandler handler,
                      @NotNull TestSuite parentSuite) {
    if (handler.shouldIgnore(aClass)) return;
    try {
      TestSuite suite = handler.createTestSuite();
      suite.setName(aClass.getSimpleName());
      boolean foundTests = false;
      Constructor<? extends TestIndexingModeSupporter> constructor = aClass.getConstructor();
      for (Method declaredMethod : MethodSorter.getDeclaredMethods(aClass)) {
        if (!Modifier.isPublic(declaredMethod.getModifiers())) continue;
        String methodName = declaredMethod.getName();
        if (!methodName.startsWith("test")) continue;
        if (TestFrameworkUtil.isPerformanceTest(methodName, aClass.getName())) continue;
        if (handler.shouldIgnore(declaredMethod)) continue;
        TestIndexingModeSupporter aCase = constructor.newInstance();
        aCase.setIndexingMode(handler.getIndexingMode());
        if (aCase instanceof TestCase testCase) {
          testCase.setName(methodName);
          if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            Test wrapper = IndexingModeTestHandler.wrapForTeamCity(testCase, handler.getIndexingMode());
            suite.addTest(wrapper);
          }
          else {
            suite.addTest(testCase);
          }
        }
        else {
          parentSuite.addTest(warning(aClass.getName() + "is not a TestSuite"));
        }
        foundTests = true;
      }
      if (foundTests) {
        parentSuite.addTest(suite);
      }
    }
    catch (NoSuchMethodException e) {
      parentSuite.addTest(warning("Failed to find default constructor for " + aClass.getName() + ", see log"));
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
      parentSuite.addTest(warning("Failed to instantiate " + aClass.getName() + ", see log"));
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  abstract class IndexingModeTestHandler {
    public final String myTestSuiteName;
    public final String myTestNamePrefix;
    private final IndexingMode myIndexingMode;

    protected IndexingModeTestHandler(@NotNull String testSuiteName,
                                      @NotNull String testNamePrefix,
                                      @NotNull IndexingMode mode) {
      myTestSuiteName = testSuiteName;
      myTestNamePrefix = testNamePrefix;
      myIndexingMode = mode;
    }

    public TestSuite createTestSuite() {
      return new NamedTestSuite(myTestNamePrefix);
    }

    public abstract boolean shouldIgnore(@NotNull AnnotatedElement aClass);

    public @NotNull TestIndexingModeSupporter.IndexingMode getIndexingMode() {
      return myIndexingMode;
    }

    private static boolean shouldIgnoreInFullIndexSuite(@NotNull AnnotatedElement element) {
      return element.isAnnotationPresent(NeedsIndex.SmartMode.class);
    }

    private static boolean shouldIgnoreInRuntimeOnlyIndexSuite(@NotNull AnnotatedElement element) {
      return shouldIgnoreInFullIndexSuite(element) || element.isAnnotationPresent(NeedsIndex.Full.class);
    }

    private static boolean shouldIgnoreInEmptyIndexSuite(@NotNull AnnotatedElement element) {
      return shouldIgnoreInRuntimeOnlyIndexSuite(element) || element.isAnnotationPresent(NeedsIndex.ForStandardLibrary.class);
    }

    private static Test wrapForTeamCity(@NotNull TestCase testCase, @NotNull IndexingMode mode) {
      return new MyHackyJUnitTaskMirrorImpl.VmExitErrorTest(testCase, mode);
    }

    private static final class NamedTestSuite extends TestSuite {
      private final String myPrefix;

      private NamedTestSuite(@NotNull String prefix) {
        myPrefix = prefix;
      }

      @Override
      public void setName(String name) {
        super.setName(myPrefix + name);
      }
    }

    /**
     * TeamCity prints log with {@code jetbrains.buildServer.ant.junit.AntJUnitFormatter3}
     * (see org.jetbrains.intellij.build.impl.TestingTaskImpl), which in TC sources
     * in {@code jetbrains.buildServer.ant.junit.JUnitUtil#getTestName} uses either className.methodName template or toString() value
     * in case it {@code startsWith(className + ".")} or {@code endsWith("JUnitTaskMirrorImpl$VmExitErrorTest")}
     * <p>
     * To test TeamCity output locally one needs to run tests from {@code tests_in_ultimate.gant} with provided environment variable
     * {@code TEAMCITY_VERSION}  (otherwise output would be formatted
     * with {@link org.jetbrains.intellij.build.JUnitLiveTestProgressFormatter}) and provided system property {@code agent.home.dir},
     * with path of buildAgent directory in TeamCity installation. To get it unpack TeamCity archive and start TeamCity with
     * {@code ./bin/runAll.sh start}
     * <p>
     * Also, these properties may be useful for debugging of tests, making them wait for remote debug connection:
     * {@code
     * -Dintellij.build.test.debug.port=<port>
     * -Dintellij.build.test.debug.suspend=true
     * }
     */
    private static class MyHackyJUnitTaskMirrorImpl {
      private static class VmExitErrorTest implements Test, Describable {
        private final TestCase myTestCase;
        private final IndexingMode myMode;

        private VmExitErrorTest(@NotNull TestCase testCase,
                                @NotNull IndexingMode mode) {
          myTestCase = testCase;
          myMode = mode;
        }

        @Override
        public int countTestCases() {
          return myTestCase.countTestCases();
        }

        @Override
        public void run(TestResult result) {
          result.startTest(this);
          Protectable p = new Protectable() {
            @Override
            public void protect() throws Throwable {
              myTestCase.runBare();
            }
          };
          result.runProtected(this, p);

          result.endTest(this);
        }

        @Override
        public String toString() {
          return myTestCase.getClass().getName() + "." + myTestCase.getName() + " with IndexingMode " + myMode.name();
        }

        @Override
        public Description getDescription() {
          return Description.createTestDescription(myTestCase.getClass(), myTestCase.getName() + "[" + myMode.name() + "]");
        }
      }
    }
  }

  class FullIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {
    public FullIndexSuite() {
      super("Full index", "Full index ", IndexingMode.DUMB_FULL_INDEX);
    }

    @Override
    public boolean shouldIgnore(@NotNull AnnotatedElement aClass) {
      //noinspection UnnecessarilyQualifiedStaticUsage
      return IndexingModeTestHandler.shouldIgnoreInFullIndexSuite(aClass);
    }
  }

  class RuntimeOnlyIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {

    public RuntimeOnlyIndexSuite() {
      super("RuntimeOnlyIndex", "Runtime only index ", IndexingMode.DUMB_RUNTIME_ONLY_INDEX);
    }

    @Override
    public boolean shouldIgnore(@NotNull AnnotatedElement aClass) {
      //noinspection UnnecessarilyQualifiedStaticUsage
      return IndexingModeTestHandler.shouldIgnoreInRuntimeOnlyIndexSuite(aClass);
    }
  }

  class EmptyIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {

    public EmptyIndexSuite() {
      super("Empty index", "Empty index ", DUMB_EMPTY_INDEX);
    }

    @Override
    public boolean shouldIgnore(@NotNull AnnotatedElement aClass) {
      //noinspection UnnecessarilyQualifiedStaticUsage
      return IndexingModeTestHandler.shouldIgnoreInEmptyIndexSuite(aClass);
    }
  }
}
