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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.DebugReflectionUtil;
import com.intellij.util.DebugReflectionUtil.BackLink;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.Queue;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * User: cdr
 */
public class LeakHunter {

  private static void walkObjects(@NotNull final Class<?> lookFor, @NotNull Collection<Object> startRoots, @NotNull final Processor<BackLink> leakProcessor) {
    TIntHashSet visited = new TIntHashSet();
    final Queue<BackLink> toVisit = new Queue<>(1000000);
    for (Object startRoot : startRoots) {
      toVisit.addLast(new BackLink(startRoot, null, null));
    }
    while (true) {
      if (toVisit.isEmpty()) return;
      final BackLink backLink = toVisit.pullFirst();
      Object root = backLink.value;
      if (!visited.add(System.identityHashCode(root))) continue;
      DebugReflectionUtil.processStronglyReferencedValues(root, (value, field) -> {
        Class<?> valueClass = value.getClass();
        if (lookFor.isAssignableFrom(valueClass) && isReallyLeak(value)) {
          leakProcessor.process(new BackLink(value, field, backLink));
        }
        else {
          toVisit.addLast(new BackLink(value, field, backLink));
        }
        return true;
      });
    }
  }

  private static final Key<Boolean> IS_NOT_A_LEAK = Key.create("IS_NOT_A_LEAK");
  public static void markAsNotALeak(@NotNull UserDataHolder object) {
    object.putUserData(IS_NOT_A_LEAK, Boolean.TRUE);
  }
  private static boolean isReallyLeak(Object value) {
    return !(value instanceof UserDataHolder) || ((UserDataHolder)value).getUserData(IS_NOT_A_LEAK) == null;
  }

  private static final Key<Boolean> REPORTED_LEAKED = Key.create("REPORTED_LEAKED");
  @TestOnly
  public static void checkProjectLeak() throws Exception {
    Processor<Project> isReallyLeak = project -> !project.isDefault() && !((ProjectImpl)project).isLight();
    Collection<Object> roots = new ArrayList<>(allRoots());
    ClassLoader classLoader = LeakHunter.class.getClassLoader();
    Vector<Class> allLoadedClasses = ReflectionUtil.getField(classLoader.getClass(), classLoader, Vector.class, "classes");
    roots.addAll(allLoadedClasses); // inspect static fields of all loaded classes
    checkLeak(roots, ProjectImpl.class, isReallyLeak);
  }

  @TestOnly
  public static void checkLeak(@NotNull Object root, @NotNull Class suspectClass) throws AssertionError {
    checkLeak(root, suspectClass, null);
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Collection<Object> roots, @NotNull Class<T> suspectClass, @Nullable final Processor<? super T> isReallyLeak) throws AssertionError {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    PersistentEnumeratorBase.clearCacheForTests();
    walkObjects(suspectClass, roots, new Processor<BackLink>() {
      @SuppressWarnings("UseOfSystemOutOrSystemErr")
      @Override
      public boolean process(BackLink backLink) {
        //noinspection unchecked
        T leaked = (T)backLink.value;
        if (markLeaked(leaked) && (isReallyLeak == null || isReallyLeak.process(leaked))) {
          String place = leaked instanceof Project ? PlatformTestCase.getCreationPlace((Project)leaked) : "";
          System.out.println("Leaked object found:" + leaked +
                             "; hash: " + System.identityHashCode(leaked) + "; place: " + place);
          System.out.println(backLink);
          System.out.println(";-----");

          throw new AssertionError();
        }
        return true;
      }

      private boolean markLeaked(T leaked) {
        return !(leaked instanceof UserDataHolderEx) || ((UserDataHolderEx)leaked).replace(REPORTED_LEAKED, null, Boolean.TRUE);
      }
    });
  }
  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Object root, @NotNull Class<T> suspectClass, @Nullable final Processor<? super T> isReallyLeak) throws AssertionError {
    checkLeak(Collections.singletonList(root), suspectClass, isReallyLeak);
  }

  @NotNull
  public static List<Object> allRoots() {
    return Arrays.asList(ApplicationManager.getApplication(), Disposer.getTree(), IdeEventQueue.getInstance(), LaterInvocator.getLaterInvocatorQueue());
  }
}
