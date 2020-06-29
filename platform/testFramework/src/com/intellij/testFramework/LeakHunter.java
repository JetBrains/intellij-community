// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.project.TestProjectManager;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.ref.DebugReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class LeakHunter {
  @TestOnly
  public static void checkProjectLeak() {
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
      String place = leaked instanceof Project ? TestProjectManager.getCreationPlace((Project)leaked) : "";
      String message ="Found leaked "+leaked.getClass() + ": "+leaked +
                      "; hash: " + System.identityHashCode(leaked) + "; place: " + place + "\n" +
                      backLink;
      System.out.println(message);
      System.out.println(";-----");
      UsefulTestCase.printThreadDump();

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
    PersistentEnumeratorBase.clearCacheForTests();
    Runnable runnable = () -> {
      try (AccessToken ignored = ProhibitAWTEvents.start("checking for leaks")) {
        DebugReflectionUtil.walkObjects(10000, rootsSupplier.get(), suspectClass, o -> true, (value, backLink) -> {
          @SuppressWarnings("unchecked")
          T leaked = (T)value;
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

  @NotNull
  public static Supplier<Map<Object, String>> allRoots() {
    return () -> {
      ClassLoader classLoader = LeakHunter.class.getClassLoader();
      // inspect static fields of all loaded classes
      @SuppressWarnings("UseOfObsoleteCollectionType")
      Vector<?> allLoadedClasses = ReflectionUtil.getField(classLoader.getClass(), classLoader, Vector.class, "classes");

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
      result.put(LaterInvocator.getLaterInvocatorWtQueue(), "LaterInvocator.getLaterInvocatorWtQueue()");
      result.put(ThreadTracker.getThreads().values(), "all live threads");
      if (allLoadedClasses != null) {
        result.put(allLoadedClasses, "all loaded classes statics");
      }
      return result;
    };
  }
}
