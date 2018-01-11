/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.ref.DebugReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

public class LeakHunter {
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
  public static <T> void checkLeak(@NotNull Collection<Object> roots,
                                   @NotNull Class<T> suspectClass,
                                   @Nullable final Condition<? super T> isReallyLeak) throws AssertionError {
    processLeaks(roots, suspectClass, isReallyLeak, (leaked, backLink)->{
      String place = leaked instanceof Project ? PlatformTestCase.getCreationPlace((Project)leaked) : "";
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
  static <T> void processLeaks(@NotNull Collection<Object> roots,
                               @NotNull Class<T> suspectClass,
                               @Nullable final Condition<? super T> isReallyLeak,
                               @NotNull final PairProcessor<? super T, Object> processor) throws AssertionError {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    PersistentEnumeratorBase.clearCacheForTests();
    LaterInvocator.purgeExpiredItems();
    ApplicationManager.getApplication().runReadAction(() -> {
      DebugReflectionUtil.walkObjects(10000, roots, suspectClass, Conditions.alwaysTrue(), (value, backLink) -> {
        @SuppressWarnings("unchecked")
        T leaked = (T)value;
        if (isReallyLeak == null || isReallyLeak.value(leaked)) {
          return processor.process(leaked, backLink);
        }
        return true;
      });
    });
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Object root, @NotNull Class<T> suspectClass, @Nullable final Condition<? super T> isReallyLeak) throws AssertionError {
    checkLeak(Collections.singletonList(root), suspectClass, isReallyLeak);
  }

  @NotNull
  public static List<Object> allRoots() {
    ClassLoader classLoader = LeakHunter.class.getClassLoader();
    // inspect static fields of all loaded classes
    Vector<Class> allLoadedClasses = ReflectionUtil.getField(classLoader.getClass(), classLoader, Vector.class, "classes");

    return Arrays.asList(ApplicationManager.getApplication(),
                         Disposer.getTree(),
                         IdeEventQueue.getInstance(),
                         LaterInvocator.getLaterInvocatorQueue(),
                         ThreadTracker.getThreads(),
                         allLoadedClasses);
  }
}
