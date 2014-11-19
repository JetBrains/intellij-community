/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.Processor;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.Queue;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: cdr
 */
public class LeakHunter {
  private static final Map<Class, Field[]> allFields = new THashMap<Class, Field[]>();
  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
  private static final Processor<Project> NOT_DEFAULT_PROJECT = new Processor<Project>() {
    @Override
    public boolean process(Project project) {
      return !project.isDefault();
    }
  };

  @NotNull
  private static Field[] getAllFields(@NotNull Class aClass) {
    Field[] cached = allFields.get(aClass);
    if (cached == null) {
      Field[] declaredFields = aClass.getDeclaredFields();
      List<Field> fields = new ArrayList<Field>(declaredFields.length + 5);
      for (Field declaredField : declaredFields) {
        declaredField.setAccessible(true);
        Class<?> type = declaredField.getType();
        if (isTrivial(type)) continue; // unable to hold references, skip
        fields.add(declaredField);
      }
      Class superclass = aClass.getSuperclass();
      if (superclass != null) {
        for (Field sup : getAllFields(superclass)) {
          if (!fields.contains(sup)) {
            fields.add(sup);
          }
        }
      }
      cached = fields.isEmpty() ? EMPTY_FIELD_ARRAY : fields.toArray(new Field[fields.size()]);
      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static boolean isTrivial(@NotNull Class<?> type) {
    return type.isPrimitive() || type == String.class || type == Class.class || type == Object.class ||
        type.isArray() && isTrivial(type.getComponentType());
  }

  private static class BackLink {
    private final Object value;
    private final Field field;
    private final BackLink backLink;

    private BackLink(@NotNull Object value, Field field, BackLink backLink) {
      this.value = value;
      this.field = field;
      this.backLink = backLink;
    }
  }

  private static void walkObjects(@NotNull Class<?> lookFor,
                                  @NotNull Object startRoot,
                                  @NotNull Processor<BackLink> leakProcessor) {
    TIntHashSet visited = new TIntHashSet();
    Queue<BackLink> toVisit = new Queue<BackLink>(1000000);
    toVisit.addLast(new BackLink(startRoot, null, null));
    while (true) {
      if (toVisit.isEmpty()) return;
      BackLink backLink = toVisit.pullFirst();
      Object root = backLink.value;
      if (!visited.add(System.identityHashCode(root))) continue;
      Class rootClass = root.getClass();
      for (Field field : getAllFields(rootClass)) {
        String fieldName = field.getName();
        if (root instanceof Reference && "referent".equals(fieldName)) continue; // do not follow weak/soft refs
        Object value;
        try {
          value = field.get(root);
        }
        catch (IllegalArgumentException e) {
          throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (value == null) continue;
        Class<?> valueClass = value.getClass();
        if (lookFor.isAssignableFrom(valueClass) && isReallyLeak(field, fieldName, value, valueClass)) {
          BackLink newBackLink = new BackLink(value, field, backLink);
          leakProcessor.process(newBackLink);
        }
        else {
          BackLink newBackLink = new BackLink(value, field, backLink);
          toVisit.addLast(newBackLink);
        }
      }
      if (rootClass.isArray()) {
        try {
          for (Object o : (Object[])root) {
            if (o == null) continue;
            if (isTrivial(o.getClass())) continue;
            toVisit.addLast(new BackLink(o, null, backLink));
          }
        }
        catch (ClassCastException ignored) {
        }
      }
    }
  }

  private static final Key<Boolean> IS_NOT_A_LEAK = Key.create("IS_NOT_A_LEAK");
  public static void markAsNotALeak(@NotNull UserDataHolder object) {
    object.putUserData(IS_NOT_A_LEAK, Boolean.TRUE);
  }
  private static boolean isReallyLeak(Field field, String fieldName, Object value, Class valueClass) {
    return !(value instanceof UserDataHolder) || ((UserDataHolder)value).getUserData(IS_NOT_A_LEAK) == null;
  }

  private static final Key<Boolean> REPORTED_LEAKED = Key.create("REPORTED_LEAKED");
  @TestOnly
  public static void checkProjectLeak() throws Exception {
    checkLeak(ApplicationManager.getApplication(), ProjectImpl.class);
    checkLeak(Extensions.getRootArea(), ProjectImpl.class, NOT_DEFAULT_PROJECT);
  }

  @TestOnly
  public static void checkLeak(@NotNull Object root, @NotNull Class suspectClass) throws AssertionError {
    checkLeak(root, suspectClass, null);
  }

  /**
   * Checks if there is a memory leak if an object of type {@code suspectClass} is strongly accessible via references from the {@code root} object.
   */
  @TestOnly
  public static <T> void checkLeak(@NotNull Object root, @NotNull Class<T> suspectClass, @Nullable final Processor<? super T> isReallyLeak) throws AssertionError {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    PersistentEnumeratorBase.clearCacheForTests();
    walkObjects(suspectClass, root, new Processor<BackLink>() {
      @Override
      public boolean process(BackLink backLink) {
        UserDataHolder leaked = (UserDataHolder)backLink.value;
        if (((UserDataHolderBase)leaked).replace(REPORTED_LEAKED, null, Boolean.TRUE) &&
            (isReallyLeak == null || isReallyLeak.process((T)leaked))) {
          String place = leaked instanceof Project ? PlatformTestCase.getCreationPlace((Project)leaked) : "";
          System.out.println("Leaked object found:" + leaked +
                             "; hash: " + System.identityHashCode(leaked) + "; place: " + place);
          while (backLink != null) {
            String valueStr;
            try {
              valueStr = backLink.value instanceof FList ? "FList" : backLink.value instanceof Collection ? "Collection" : String.valueOf(backLink.value);
            }
            catch (Throwable e) {
              valueStr = "(" + e.getMessage() + " while computing .toString())";
            }
            System.out.println("-->" + backLink.field + "; Value: " + valueStr + "; " + backLink.value.getClass());
            backLink = backLink.backLink;
          }
          System.out.println(";-----");

          throw new AssertionError();
        }
        return true;
      }
    });
  }
}
