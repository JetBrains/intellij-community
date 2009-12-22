/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeAction;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

@SuppressWarnings({"SSBasedInspection"})
public class Disposer {
  private static final ObjectTree<Disposable> ourTree = new ObjectTree<Disposable>();

  private static final ObjectTreeAction<Disposable> ourDisposeAction = new ObjectTreeAction<Disposable>() {
    public void execute(final Disposable each) {
      each.dispose();
    }

    public void beforeTreeExecution(final Disposable parent) {
      if (parent instanceof Disposable.Parent) {
        ((Disposable.Parent)parent).beforeTreeDispose();
      }
    }
  };

  private static boolean ourDebugMode;

  private Disposer() {
  }

  public static Disposable newDisposable() {
    return new Disposable() {
      public void dispose() {
      }
    };
  }

  private static final Map<String, Disposable> ourKeyDisposables = new ConcurrentWeakHashMap<String, Disposable>();

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    register(parent, child, null);
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child, @NonNls @Nullable final String key) {
    assert parent != child : " Cannot register to itself";

    ourTree.register(parent, child);

    if (key != null) {
      assert get(key) == null;
      ourKeyDisposables.put(key, child);
      register(child, new Disposable() {
        public void dispose() {
          ourKeyDisposables.remove(key);
        }
      });
    }
  }

  public static boolean isDisposed(Disposable disposable) {
    return !ourTree.containsKey(disposable);
  }

  public static Disposable get(String key) {
    return ourKeyDisposables.get(key);
  }

  public static void dispose(Disposable disposable) {
    dispose(disposable, true);
  }

  public static void dispose(Disposable disposable, boolean processUnregistered) {
    ourTree.executeAll(disposable, true, ourDisposeAction, processUnregistered);
  }

  public static void disposeChildAndReplace(Disposable toDipose, Disposable toReplace) {
    ourTree.executeChildAndReplace(toDipose, toReplace, true, ourDisposeAction);
  }

  public static ObjectTree<Disposable> getTree() {
    return ourTree;
  }

  public static void assertIsEmpty() {
    if (ourDebugMode) {
      ourTree.assertIsEmpty();
    }
  }

  public static boolean isEmpty() {
    return ourDebugMode && ourTree.isEmpty();
  }

  public static void setDebugMode(final boolean b) {
    ourDebugMode = b;
  }

  public static boolean isDebugMode() {
    return ourDebugMode;
  }

  public static void clearOwnFields(Object object) {
    final Field[] all = object.getClass().getDeclaredFields();
    for (Field each : all) {
      if ((each.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) > 0) continue;
      ReflectionUtil.resetField(object, each);
    }
  }

  /**
   * @return object registered on parentDisposable which is equal to object, or null
   */
  @Nullable
  public static <T extends Disposable> T findRegisteredObject(@NotNull Disposable parentDisposable, @NotNull T object) {
    return ourTree.findRegisteredObject(parentDisposable, object);
  }
}
