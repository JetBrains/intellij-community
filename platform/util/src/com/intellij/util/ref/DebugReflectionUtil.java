// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ref;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.Queue;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class DebugReflectionUtil {
  private static final Map<Class<?>, Field[]> allFields = new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<Class<?>>() {
    // default strategy seems to be too slow
    @Override
    public int hashCode(@Nullable Class<?> aClass) {
      return aClass == null ? 0 : aClass.getName().hashCode();
    }

    @Override
    public boolean equals(@Nullable Class<?> o1, @Nullable Class<?> o2) {
      return o1 == o2;
    }
  });

  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
  private static final Method Unsafe_shouldBeInitialized = ReflectionUtil.getDeclaredMethod(Unsafe.class, "shouldBeInitialized", Class.class);

  private static Field @NotNull [] getAllFields(@NotNull Class<?> aClass) {
    Field[] cached = allFields.get(aClass);
    if (cached == null) {
      try {
        Field[] declaredFields = aClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>(declaredFields.length + 5);
        for (Field declaredField : declaredFields) {
          declaredField.setAccessible(true);
          Class<?> type = declaredField.getType();
          if (isTrivial(type)) continue; // unable to hold references, skip
          fields.add(declaredField);
        }
        Class<?> superclass = aClass.getSuperclass();
        if (superclass != null) {
          for (Field sup : getAllFields(superclass)) {
            if (!fields.contains(sup)) {
              fields.add(sup);
            }
          }
        }
        cached = fields.isEmpty() ? EMPTY_FIELD_ARRAY : fields.toArray(new Field[0]);
      }
      catch (IncompatibleClassChangeError | NoClassDefFoundError | SecurityException e) {
        //this exception may be thrown because there are two different versions of org.objectweb.asm.tree.ClassNode from different plugins
        //I don't see any sane way to fix it until we load all the plugins by the same classloader in tests
        cached = EMPTY_FIELD_ARRAY;
      }
      catch (@ReviseWhenPortedToJDK("9") RuntimeException e) {
        // field.setAccessible() can now throw this exception when accessing unexported module
        if (e.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
          cached = EMPTY_FIELD_ARRAY;
        }
        else {
          throw e;
        }
      }

      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static boolean isTrivial(@NotNull Class<?> type) {
    return type.isPrimitive() || type == String.class || type == Class.class || type.isArray() && isTrivial(type.getComponentType());
  }

  private static boolean isInitialized(@NotNull Class<?> root) {
    if (Unsafe_shouldBeInitialized == null) return false;
    boolean isInitialized = false;
    try {
      isInitialized = !(Boolean)Unsafe_shouldBeInitialized.invoke(AtomicFieldUpdater.getUnsafe(), root);
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    return isInitialized;
  }

  private static final Key<Boolean> REPORTED_LEAKED = Key.create("REPORTED_LEAKED");

  public static boolean walkObjects(int maxDepth,
                                    @NotNull Map<Object, String> startRoots,
                                    @NotNull final Class<?> lookFor,
                                    @NotNull Condition<Object> shouldExamineValue,
                                    @NotNull final PairProcessor<Object, ? super BackLink> leakProcessor) {
    IntSet visited = new IntOpenHashSet(100);
    Queue<BackLink> toVisit = new Queue<>(100);

    for (Map.Entry<Object, String> entry : startRoots.entrySet()) {
      Object startRoot = entry.getKey();
      final String description = entry.getValue();
      toVisit.addLast(new BackLink(startRoot, null, null){
        @NotNull
        @Override
        String print() {
          return super.print() +" (from "+description+")";
        }
      });
    }

    while (true) {
      if (toVisit.isEmpty()) {
        return true;
      }
      final BackLink backLink = toVisit.pullFirst();
      if (backLink.depth > maxDepth) {
        continue;
      }
      Object value = backLink.value;
      if (lookFor.isAssignableFrom(value.getClass()) && markLeaked(value) && !leakProcessor.process(value, backLink)) {
        return false;
      }

      if (visited.add(System.identityHashCode(value))) {
        queueStronglyReferencedValues(toVisit, value, shouldExamineValue, backLink);
      }
    }
  }

  private static void queueStronglyReferencedValues(@NotNull Queue<? super BackLink> queue,
                                                    @NotNull Object root,
                                                    @NotNull Condition<Object> shouldExamineValue,
                                                    @NotNull BackLink backLink) {
    Class<?> rootClass = root.getClass();
    for (Field field : getAllFields(rootClass)) {
      String fieldName = field.getName();
      if (root instanceof Reference && ("referent".equals(fieldName) || "discovered".equals(fieldName))) continue; // do not follow weak/soft refs
      Object value;
      try {
        value = field.get(root);
      }
      catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }

      queue(value, field, backLink, queue, shouldExamineValue);
    }
    if (rootClass.isArray()) {
      try {
        for (Object value : (Object[])root) {
          queue(value, null, backLink, queue, shouldExamineValue);
        }
      }
      catch (ClassCastException ignored) {
      }
    }
    // check for objects leaking via static fields. process initialized classes only
    if (root instanceof Class && isInitialized((Class<?>)root)) {
        for (Field field : getAllFields((Class<?>)root)) {
          if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
          try {
            Object value = field.get(null);
            queue(value, field, backLink, queue, shouldExamineValue);
          }
          catch (IllegalAccessException ignored) {
          }
        }
    }
  }

  private static void queue(Object value, Field field, @NotNull BackLink backLink, @NotNull Queue<? super BackLink> queue,
                            @NotNull Condition<Object> shouldExamineValue) {
    if (value == null || isTrivial(value.getClass())) return;
    if (shouldExamineValue.value(value)) {
      BackLink newBackLink = new BackLink(value, field, backLink);
      queue.addLast(newBackLink);
    }
  }

  private static boolean markLeaked(Object leaked) {
    return !(leaked instanceof UserDataHolderEx) || ((UserDataHolderEx)leaked).replace(REPORTED_LEAKED, null, Boolean.TRUE);
  }

  public static class BackLink {
    @NotNull private final Object value;
    private final Field field;
    private final BackLink backLink;
    private final int depth;

    BackLink(@NotNull Object value, @Nullable Field field, @Nullable BackLink backLink) {
      this.value = value;
      this.field = field;
      this.backLink = backLink;
      depth = backLink == null ? 0 : backLink.depth + 1;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      BackLink backLink = this;
      while (backLink != null) {
        result.append(backLink.print());
        backLink = backLink.backLink;
      }
      return result.toString();
    }

    @NotNull
    String print() {
      String valueStr;
      Object value = this.value;
      try {
        valueStr = value instanceof FList
                   ? "FList (size=" + ((FList<?>)value).size() + ")" :
                   value instanceof Collection ? "Collection (size=" + ((Collection<?>)value).size() + ")" :
                   String.valueOf(value);
        valueStr = StringUtil.first(StringUtil.convertLineSeparators(valueStr, "\\n"), 200, true);
      }
      catch (Throwable e) {
        valueStr = "(" + e.getMessage() + " while computing .toString())";
      }
      Field field = this.field;
      String fieldName = field == null ? "?" : field.getDeclaringClass().getName() + "." + field.getName();
      return "via '" + fieldName + "'; Value: '" + valueStr + "' of " + value.getClass() + "\n";
    }
  }
}
