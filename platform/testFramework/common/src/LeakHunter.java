// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.testFramework.common.DumpKt;
import com.intellij.testFramework.common.TestApplicationKt;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.PersistentEnumeratorCache;
import com.intellij.util.ref.DebugReflectionUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LeakHunter {

  @TestOnly
  public static @NotNull String getCreationPlace(@NotNull Project project) {
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
    processLeaks(rootsSupplier, suspectClass, isReallyLeak, (leaked, backLink)->{
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
  public static <T> void processLeaks(@NotNull Supplier<? extends Map<Object, String>> rootsSupplier,
                                      @NotNull Class<T> suspectClass,
                                      @Nullable Predicate<? super T> isReallyLeak,
                                      @NotNull PairProcessor<? super T, Object> processor) throws AssertionError {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    PersistentEnumeratorCache.clearCacheForTests();
    flushTelemetry();
    GCUtil.tryGcSoftlyReachableObjects();
    Runnable runnable = () -> {
      try (AccessToken ignored = ProhibitAWTEvents.start("checking for leaks")) {
        DebugReflectionUtil.walkObjects(10000, rootsSupplier.get(), suspectClass, __ -> true, (leaked, backLink) -> {
          if (isReallyLeak == null || isReallyLeak.test(leaked)) {
            return processor.process(leaked, backLink);
          }
          return true;
        });
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      runnable.run();
    }
    else {
      application.runReadAction(runnable);
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
      ClassLoader classLoader = LeakHunter.class.getClassLoader();
      // inspect static fields of all loaded classes
      Collection<?> allLoadedClasses = ReflectionUtil.getField(classLoader.getClass(), classLoader, Vector.class, "classes");

      // Remove expired invocations, so they are not used as object roots.
      LaterInvocator.purgeExpiredItems();

      Map<Object, String> result = new IdentityHashMap<>();
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        result.put(application, "ApplicationManager.getApplication()");
      }
      result.put(Disposer.getTree(), "Disposer.getTree()");
      result.put(IdeEventQueue.getInstance(), "IdeEventQueue.getInstance()");
      result.put(LaterInvocator.getLaterInvocatorEdtQueue(), "LaterInvocator.getLaterInvocatorEdtQueue()");
      result.put(ThreadLeakTracker.getThreads().values(), "all live threads");
      if (allLoadedClasses != null) {
        result.put(allLoadedClasses, "all loaded classes statics");
      }
      return result;
    };
  }

  @TestOnly
  @NotNull
  public static String getLeakedObjectDetails(@NotNull Object leaked,
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
      result+="\n  You can find a memory snapshot `"
        +TestApplicationKt.LEAKED_PROJECTS
        +".hproof.zip` in the \"Artifacts\" tab of the build run.";
      result+="\n  If you suspect a particular test, you can reproduce the problem locally " +
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
    //noinspection TestOnlyProblems
    TelemetryManager.getInstance().forceFlushMetricsBlocking();
  }
}
