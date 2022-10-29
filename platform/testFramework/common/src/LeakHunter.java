// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.PersistentEnumeratorCache;
import com.intellij.util.ref.DebugReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.testFramework.common.DumpKt.HEAP_DUMP_IS_PUBLISHED;
import static com.intellij.testFramework.common.TestApplicationKt.LEAKED_PROJECTS;

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
      StringBuilder builder = new StringBuilder();
      appendLeakedObjectDetails(builder, leaked, backLink, true);
      String message = builder.toString();

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
  public static void appendLeakedObjectDetails(
    @NotNull StringBuilder builder,
    @NotNull Object leaked,
    @Nullable Object backLink,
    boolean detailedErrorDescription
  ) {
    String creationPlace = leaked instanceof Project ? getCreationPlace((Project)leaked) : null;
    int hashCode = System.identityHashCode(leaked);

    builder.append("Found a leaked instance of ").append(leaked.getClass())
      .append("\nInstance: ").append(leaked)
      .append("\nHashcode: ").append(hashCode);
    if (detailedErrorDescription) {
      appendLeakedObjectErrorDescription(builder, null);
    }
    if (backLink != null) {
      builder.append("\nExisting strong reference path to the instance:\n")
        .append(backLink.toString().indent(2));
    }

    if (creationPlace != null) {
      builder.append("\nThe instance was created at: ").append(creationPlace);
    }
  }

  @TestOnly
  public static void appendLeakedObjectErrorDescription(@NotNull StringBuilder builder, @Nullable String knownHeapDumpPath) {
    builder.append("\nError description:")
      .append("\n  This error means that the object is expected to be collected by the GC by this time, but it was not.")
      .append("\n  Please, make sure you dispose your resources properly. See https://plugins.jetbrains.com/docs/intellij/disposers.html");

    if (knownHeapDumpPath != null) {
      builder.append("\n  Please see `").append(knownHeapDumpPath).append("` for a memory dump");
    }
    else {
      builder.append("\n  If this is a TC build, you can find a memory snapshot `").append(LEAKED_PROJECTS).append(".hproof.zip` in the \"Artifacts\" tab of the build run.")
        .append("\n  Otherwise, try looking for '").append(HEAP_DUMP_IS_PUBLISHED).append("' string in the system output below ↓.");
    }
  }
}
