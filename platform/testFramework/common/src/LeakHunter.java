// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.common.DumpKt;
import com.intellij.testFramework.common.TestApplicationKt;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.PersistentEnumeratorCache;
import com.intellij.util.ref.DebugReflectionUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.IgnoredTraverseEntry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

@TestOnly
public final class LeakHunter {
  @TestOnly
  private static @NotNull String getCreationPlace(@NotNull Project project) {
    String creationTrace = project instanceof ProjectEx ? ((ProjectEx)project).getCreationTrace() : null;
    return project + " " + (creationTrace == null ? " " : creationTrace);
  }

  @TestOnly
  public static void checkProjectLeak() throws AssertionError {
    checkLeak(allRoots(), ProjectImpl.class, project -> !project.isDefault() && !project.isLight());
  }

  @TestOnly
  public static void checkNonDefaultProjectLeak() {
    checkLeak(allRoots(), ProjectImpl.class, project -> !project.isDefault());
  }

  @TestOnly
  public static void checkNonDefaultProjectLeakWithIgnoredEntries(@NotNull List<? extends IgnoredTraverseEntry> ignoredTraverseEntries) {
    checkLeak(allRoots(), ProjectImpl.class, ignoredTraverseEntries, project -> !project.isDefault());
  }

  @TestOnly
  public static void checkLeak(@NotNull Object root, @NotNull Class<?> suspectClass) throws AssertionError {
    checkLeak(root, suspectClass, null);
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Supplier<? extends Map<Object, String>> rootsSupplier,
                                   @NotNull Class<T> suspectClass,
                                   @Nullable Predicate<? super T> isReallyLeak) throws AssertionError {
    processLeaks(rootsSupplier, suspectClass, isReallyLeak, null, (leaked, backLink)->{
      String message = getLeakedObjectDetails(leaked, backLink, true);

      System.out.println(message);
      System.out.println(";-----");
      ThreadUtil.printThreadDump();

      throw new AssertionError(message);
    });
  }

  @TestOnly
  public static <T> void checkLeak(@NotNull Supplier<? extends Map<Object, String>> rootsSupplier,
                                   @NotNull Class<T> suspectClass,
                                   @NotNull List<? extends IgnoredTraverseEntry> ignoredTraverseEntries,
                                   @Nullable Predicate<? super T> isReallyLeak) throws AssertionError {
    processLeaks(rootsSupplier, suspectClass, isReallyLeak, (backLink) -> {
      for (IgnoredTraverseEntry entry : ignoredTraverseEntries) {
        if (entry.test(backLink)) {
          return true;
        }
      }
      return false;
    }, (leaked, backLink) -> {
      String message = getLeakedObjectDetails(leaked, backLink, true);

      System.out.println(message);
      System.out.println(";-----");
      ThreadUtil.printThreadDump();

      throw new AssertionError(message);
    });
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> boolean processLeaks(@NotNull Supplier<? extends Map<Object, String>> rootsSupplier,
                                         @NotNull Class<T> suspectClass,
                                         @Nullable Predicate<? super T> isReallyLeak,
                                         @Nullable Predicate<? super DebugReflectionUtil.BackLink<?>> leakBackLinkProcessor,
                                         @NotNull PairProcessor<? super T, Object> processor) throws AssertionError {
    tryClearingNonReachableObjects();

    Computable<Boolean> runnable = () -> {
      try (AccessToken ignored = ProhibitAWTEvents.start("checking for leaks")) {
        return DebugReflectionUtil.walkObjects(10000, 10_000_000, rootsSupplier.get(), suspectClass, __ -> true, (leaked, backLink) -> {
          if (leakBackLinkProcessor != null && leakBackLinkProcessor.test(backLink)) {
            return true;
          }
          if (isReallyLeak == null || isReallyLeak.test(leaked)) {
            return processor.process(leaked, backLink);
          }
          return true;
        });
      }
    };
    Application application = ApplicationManager.getApplication();
    return application == null ? runnable.compute() : application.runReadAction(runnable);
  }

  // we want to avoid walking heap during indexing, because zillions of UpdateOp and other transient indexing requests stored in the temp queue could OOME
  @TestOnly
  private static void waitForIndicesToUpdate() {
    ProjectManager projectManager = ApplicationManager.getApplication() == null ? null : ProjectManager.getInstance();
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
      for (Project project : projectManager == null ? new Project[0] : projectManager.getOpenProjects()) {
        while (DumbService.getInstance(project).isDumb()) {
          DumbService.getInstance(project).waitForSmartMode(100L);
          UIUtil.dispatchAllInvocationEvents();
        }
        FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));
      }
    }
    else {
      for (Project project : projectManager == null ? new Project[0] : projectManager.getOpenProjects()) {
        DumbService.getInstance(project).waitForSmartMode();
        FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));
      }
      UIUtil.pump();
    }
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Object root, @NotNull Class<T> suspectClass, @Nullable Predicate<? super T> isReallyLeak) throws AssertionError {
    checkLeak(() -> Collections.singletonMap(root, "Root object"), suspectClass, isReallyLeak);
  }

  @TestOnly
  public static @NotNull Supplier<Map<Object, String>> allRoots() {
    return () -> {
      Map<Object, String> result = new IdentityHashMap<>();
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        result.put(application, "ApplicationManager.getApplication()");
      }
      result.put(Disposer.getTree(), "Disposer.getTree()");
      result.put(IdeEventQueue.getInstance(), "IdeEventQueue.getInstance()");
      result.put(LaterInvocator.getLaterInvocatorEdtQueue(), "LaterInvocator.getLaterInvocatorEdtQueue()");
      result.put(ThreadLeakTracker.getThreads().values(), "all live threads");
      ClassLoader classLoader = LeakHunter.class.getClassLoader();
      // inspect static fields of all loaded classes
      Collection<?> allLoadedClasses = ReflectionUtil.getField(classLoader.getClass(), classLoader, Vector.class, "classes");
      if (allLoadedClasses != null) {
        result.put(allLoadedClasses, "all loaded classes statics");
      }
      return result;
    };
  }

  // perform as many magic tricks as possible to clear the memory of stale objects:
  // index update queues, caches, references to dangling threads/coroutines/invokeLaters, etc
  @TestOnly
  private static void tryClearingNonReachableObjects() {
    // avoid walking heap during indexes rebuilding because they allocate huge queues with a lot of short-lived transient UpdateOp objects during the process,
    // which then are stored in the leak-hunter own queue even though they are no longer reachable
    waitForIndicesToUpdate();

    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
      // Remove expired invocations, so they are not used as object roots.
      LaterInvocator.purgeExpiredItems();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    else {
      UIUtil.pump();
      try {
        SwingUtilities.invokeAndWait(() -> {
          LaterInvocator.purgeExpiredItems();
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        });
      }
      catch (InterruptedException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    PersistentEnumeratorCache.clearCacheForTests();
    flushTelemetry();
    GCUtil.tryGcSoftlyReachableObjects();
  }

  @TestOnly
  public static @NotNull String getLeakedObjectDetails(@NotNull Object leaked,
                                                       @Nullable Object backLink,
                                                       boolean detailedErrorDescription) {
    int hashCode = System.identityHashCode(leaked);
    String result = "Found a leaked instance of "+leaked.getClass()
                    +"\nInstance: "+leaked
                    +"\nHashcode: "+hashCode;
    if (detailedErrorDescription) {
      result += "\n"+getLeakedObjectErrorDescription(null);
    }
    if (backLink != null) {
      result += "\nExisting strong reference path to the instance:\n" +backLink.toString().indent(2);
    }

    String creationPlace = leaked instanceof Project ? getCreationPlace((Project)leaked) : null;
    if (creationPlace != null) {
      result += "\nThe instance was created at: "+creationPlace;
    }
    return result;
  }

  @TestOnly
  public static @NotNull String getLeakedObjectErrorDescription(@Nullable String knownHeapDumpPath) {
    String result = """
      Error description:
        This error means that the object is expected to be collected by the garbage collector by this time, but it was not.
        Please make sure you dispose your resources properly. See https://plugins.jetbrains.com/docs/intellij/disposers.html""";

    if (TeamCityLogger.isUnderTC) {
      String buildUrl = TeamCityLogger.currentBuildUrl != null ? StringUtil.trimEnd(TeamCityLogger.currentBuildUrl, "/") : "";
      String buildArtifactsLink = !buildUrl.isBlank() ? " " + buildUrl + "?buildTab=artifacts" : "";

      result += "\n  You can find a memory snapshot `"
                + TestApplicationKt.LEAKED_PROJECTS
                + ".hprof.zip` in the \"Artifacts\" tab of the build run" + buildArtifactsLink + ".";
      result += "\n  See leaks investigation guide https://jb.gg/ijpl-project-leaks.";
      result += "\n  If you suspect a particular test, you can reproduce the problem locally " +
                "calling TestApplicationManager.testProjectLeak() after the test.";
    }
    else if (knownHeapDumpPath != null) {
      result += "\n  Please see ``" + knownHeapDumpPath + "` for a memory dump";
    }
    else {
      result += "\n  Try looking for '"+DumpKt.HEAP_DUMP_IS_PUBLISHED
        +"' line in the system output log below. It contains a path to a collected memory snapshot";
    }

    return result;
  }

  // OTel traces may store references to cancellation exceptions.
  // In the case of kotlin coroutines, a cancellation exception references `Job`, which may reference `CoroutineContext`,
  // which may reference `ComponentManager`s (such as `Project` of `Application`).
  // The traces are processed in batches, so we cannot predict when they get cleared,
  // although we know that they be cleared after a certain finite period of time.
  // Here we forcibly flush the batch and avoid a leak of component managers.
  private static void flushTelemetry() {
    TelemetryManager.getInstance().forceFlushMetricsBlocking();
  }
}
