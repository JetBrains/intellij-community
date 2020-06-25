// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.junit.internal.MethodSorter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static junit.framework.TestSuite.warning;

public interface TestIndexingModeSupporter {
  enum IndexingMode {
    SMART {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {}
    }, DUMB_FULL_INDEX {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        indexEverythingAndBecomeDumb(project);
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
      }
    }, DUMB_EMPTY_INDEX {
      @Override
      public void setUpTest(@NotNull Project project, @NotNull Disposable testRootDisposable) {
        ServiceContainerUtil
          .replaceService(ApplicationManager.getApplication(), FileBasedIndex.class, new EmptyFileBasedIndex(), testRootDisposable);
        becomeDumb(project);
      }
    };

    public abstract void setUpTest(@NotNull Project project,
                                   @NotNull Disposable testRootDisposable);

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
          suite.addTest(testCase);
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
      e.printStackTrace();
    }
    catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
      parentSuite.addTest(warning("Failed to instantiate " + aClass.getName() + ", see log"));
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
      return new NamedTestSuite(myTestSuiteName, myTestNamePrefix);
    }

    public abstract boolean shouldIgnore(@NotNull Class<? extends TestIndexingModeSupporter> aClass);

    public abstract boolean shouldIgnore(@NotNull Method method,
                                         @NotNull Class<? extends TestIndexingModeSupporter> aClass);

    public abstract @NotNull TestIndexingModeSupporter.IndexingMode getIndexingMode();

    private static final class NamedTestSuite extends TestSuite {
      private final String myPrefix;

      private NamedTestSuite(String name, String prefix) {
        super(name);
        myPrefix = prefix;
      }

      @Override
      public void setName(String name) {
        super.setName(myPrefix + name);
      }
    }
  }
}
