// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import junit.framework.*;
import org.jetbrains.annotations.NotNull;
import org.junit.internal.MethodSorter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static junit.framework.TestSuite.warning;

/**
 * To run a test with needed {@link IndexingMode}, it's enough to make getIndexingMode return it and run the test with IDE's gutter action.
 * To run all dumb mode completion tests, check JavaDoc of
 * {@link com.intellij.java.codeInsight.completion.JavaCompletionTestSuite} or
 * {@link com.jetbrains.php.slowTests.PhpDumbCompletionTestSuite}
 */
public interface TestIndexingModeSupporter {
  enum IndexingMode {
    SMART {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {}

      @Override
      public void tearDownTest(@NotNull Project project) {}
    }, DUMB_FULL_INDEX {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        indexEverythingAndBecomeDumb(project);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
      }

      @Override
      public void ensureIndexingStatus(@NotNull Project project) {
        DumbServiceImpl dumbService = DumbServiceImpl.getInstance(project);
        ApplicationManager.getApplication().invokeAndWait(() -> {
          dumbService.setDumb(false);
          dumbService.queueTask(new UnindexedFilesUpdater(project));
          dumbService.setDumb(true);
        });
      }
    }, DUMB_RUNTIME_ONLY_INDEX {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        becomeDumb(project);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
      }
    }, DUMB_EMPTY_INDEX {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        ServiceContainerUtil
          .replaceService(ApplicationManager.getApplication(), FileBasedIndex.class, new EmptyFileBasedIndex(), testRootDisposable);
        becomeDumb(project);
        RecursionManager.disableMissedCacheAssertions(testRootDisposable);
      }
    };

    public abstract void setUpTest(@NotNull Project project,
                                   @NotNull Disposable testRootDisposable);

    public void tearDownTest(@NotNull Project project) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        DumbServiceImpl.getInstance(project).setDumb(false);
      });
    }

    public void ensureIndexingStatus(@NotNull Project project) {
    }

    private static void becomeDumb(@NotNull Project project) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        DumbServiceImpl.getInstance(project).setDumb(true);
      });
    }

    private static void indexEverythingAndBecomeDumb(@NotNull Project project) {
      DumbServiceImpl dumbService = DumbServiceImpl.getInstance(project);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        dumbService.setDumb(false);
        dumbService.queueTask(new UnindexedFilesUpdater(project));
        dumbService.setDumb(true);
      });
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
        if (handler.shouldIgnore(declaredMethod, aClass)) continue;
        TestIndexingModeSupporter aCase = constructor.newInstance();
        aCase.setIndexingMode(handler.getIndexingMode());
        if (aCase instanceof TestCase) {
          TestCase testCase = (TestCase)aCase;
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

    protected IndexingModeTestHandler(@NotNull String testSuiteName, @NotNull String testNamePrefix) {
      myTestSuiteName = testSuiteName;
      myTestNamePrefix = testNamePrefix;
    }

    public TestSuite createTestSuite() {
      return new NamedTestSuite(myTestNamePrefix);
    }

    public abstract boolean shouldIgnore(@NotNull Class<? extends TestIndexingModeSupporter> aClass);

    public abstract boolean shouldIgnore(@NotNull Method method,
                                         @NotNull Class<? extends TestIndexingModeSupporter> aClass);

    public abstract @NotNull TestIndexingModeSupporter.IndexingMode getIndexingMode();

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
     * Also these properties may be useful for debugging of tests, making them wait for remote debug connection:
     * {@code
     * -Dintellij.build.test.debug.port=<port>
     * -Dintellij.build.test.debug.suspend=true
     * }
     */
    private static class MyHackyJUnitTaskMirrorImpl {
      private static class VmExitErrorTest implements Test {
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
      }
    }
  }
}
