// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.Unmappable;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.util.io.pagecache.PagedStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import static com.intellij.openapi.util.text.StringUtil.repeat;

public final class StorageTestingUtils {
  /**
   * Emulates scenario there underlying storage(s) was closed without invoking storage.close() method -- as-if
   * app was kill-9-ed. This is just an emulation -- it doesn't replicate all the details of actual kill-9 --
   * but re-opened storage most likely will have 'not closed properly' state, so appropriate actions could be
   * tested
   * <p>
   * It is hard to test 'improperly closed' behaviour of storages because the storages APIs are intentionally
   * _designed_ to prohibit 'improperly closed' scenarios. E.g., in most cases, one can't re-open storage that
   * wasn't closed -- all the raw storages (MMappedFileStorage, PagedFileStorage, etc) keep track of opened
   * files, and prohibit re-opening of the file that is already opened and not yet closed.
   * <p>
   * The real-life scenario for 'improperly closed' is unexpected app termination -- kill or crash. Such
   * scenarios could be emulated (see StressTestUtil), but it is quite a heavy-weight testing approach.
   * <p>
   * Here is a more light-weight approach to create 'improperly closed' state: we use reflection to
   * find the ~deepest underlying (wrapped) storages, and close them -- without closing top-level storage.
   * Now one _can_ re-open the same file, and got storage in 'improperly closed' state
   */
  public static void emulateImproperClose(@NotNull AutoCloseable storage) throws Exception {
    emulateImproperClose(storage, new HashSet<>(), 0);
  }

  private static void emulateImproperClose(@NotNull Object storage,
                                           @NotNull Set<Object> alreadyProcessed,
                                           int depth) throws Exception {
    //RC: this method is by design 'hacky': we are trying to do something that storage API was NOT designed to allow,
    //    and even designed to prohibit. And since we're doing it for memory-mapped storages, this hack is inherently
    //    JVM-crash-risky.
    //
    //    Basic logic is simple: we use reflection to walk object graph starting with 'storage', look for the 'raw'
    //    (low-level) storages, like MMappedFileStorage, or PagedStorage (see the full list below), and close those
    //    low-level storages, without closing upper-level storages, built on top of those low-level raw storages.
    //    Managing of 'proper close' is a task of upper-level storage implementation -- as we don't close upper-level
    //    storages, they have no chance to stamp the 'proper close' flags into the persistent data, hence the persistent
    //    data state must be as-if app was crashed.
    //
    //    Even though it is conceptually simple, a lot of tuning and implicit assumptions needed to implement it. Basic
    //    complications on the way:
    //    1. Walk as little of object graph as possible, and not the whole heap -- but not miss any storage. For that
    //       .isUntouchable() method cuts off branches early.
    //    2. Some objects could rely on the storages we already close -- e.g. even ByteBuffer.hashCode() could crash JVM
    //       if it is mapped ByteBuffer, and it was already unmapped. Again, .isUntouchable() is the protection

    if (alreadyProcessed.contains(storage)) {
      return;
    }
    alreadyProcessed.add(storage);

    Field[] fields = collectAllFields(storage);
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers())
          || field.isSynthetic()
          || field.getType().isPrimitive()) {
        //log(depth, field, "skipped");
        continue;
      }

      try {
        field.setAccessible(true);
        Object fieldValue = field.get(storage);
        if (isUntouchable(fieldValue)) {
          //log(depth, field, "skipped");
          continue;
        }


        //'raw' storages:
        if (fieldValue instanceof MMappedFileStorage) {
          log(depth, field, "closing");
          //... and unmap because otherwise there would be issues with removing underlying file on Windows
          ((MMappedFileStorage)fieldValue).closeAndUnsafelyUnmap();
        }
        else if (fieldValue instanceof MappedFileStorageHelper) {
          //MappedFileStorageHelper keeps its own registry of opened helpers, so we should close helper explicitly,
          // and can't just dive deeper and close underlying MMappedFileStorage -- otherwise MappedFileStorageHelper
          // denies re-opening the same file later
          log(depth, field, "closing");
          //... and unmap because otherwise there would be issues with removing underlying file on Windows
          ((MappedFileStorageHelper)fieldValue).closeAndUnsafelyUnmap();
        }
        else if (fieldValue instanceof PagedStorage) {
          log(depth, field, "closing");
          ((PagedStorage)fieldValue).close();
        }
        else if (fieldValue instanceof PagedFileStorage) {
          log(depth, field, "closing");
          ((PagedFileStorage)fieldValue).close();
        }
        else {
          log(depth, field, "diving deeper...");
          emulateImproperClose(fieldValue, alreadyProcessed, depth + 1);
        }
      }
      catch (Throwable t) {
        log(depth, field, "failed: " + t.getMessage());
      }
    }
  }

  public static void bestEffortToCloseAndClean(@NotNull Object storage) throws Exception {
    bestEffortToCloseAndClean("", storage, new HashSet<>(), 0);
  }

  private static void bestEffortToCloseAndClean(@NotNull String fieldName,
                                                @NotNull Object value,
                                                @NotNull Set<Object> alreadyProcessed,
                                                int depth) throws Exception {
    if (isUntouchable(value)) {
      return;
    }
    if (alreadyProcessed.contains(value)) {
      return;
    }
    alreadyProcessed.add(value);

    if (value instanceof CleanableStorage) {
      log(depth, fieldName, "closeAndClean()");
      ((CleanableStorage)value).closeAndClean();
      return;
    }

    if (value instanceof Unmappable) {
      log(depth, fieldName, "closeAndUnmap()");
      ((Unmappable)value).closeAndUnsafelyUnmap();
      //we don't clean it, but still do our best so files _could_ be removed later/upper the stack
      return;
    }

    if (value instanceof Iterable<?>) {
      log(depth, fieldName, "iterate and dive deeper...");
      int i = 0;
      for (Object nested : (Iterable<?>)value) {
        bestEffortToCloseAndClean("[" + i + "]", nested, alreadyProcessed, depth + 1);
        i++;
      }
      return;
    }
    //MAYBE RC: iterate Object[] also?

    //Assume everything else is 'compound' storage that _may_ hold 'raw' underlying storage(s)
    // somewhere deeper:
    if (value instanceof AutoCloseable) {
      log(depth, fieldName, "close() and dive deeper...");
      ((AutoCloseable)value).close();
    }
    else if (value instanceof Disposable) {
      log(depth, fieldName, "dispose() and dive deeper...");
      Disposer.dispose((Disposable)value);
    }
    else {
      log(depth, fieldName, "just dive deeper...");
    }

    Field[] fields = collectAllFields(value);
    for (Field field : fields) {

      if (Modifier.isStatic(field.getModifiers())
          || field.isSynthetic()
          || field.getType().isPrimitive()) {
        //log(depth + 1, field, "ignore");
        continue;
      }
      try {
        field.setAccessible(true);
        Object fieldValue = field.get(value);
        if (fieldValue == null) {
          //log(depth + 1, field, "ignore null");
          continue;
        }

        bestEffortToCloseAndClean(field.getName(), fieldValue, alreadyProcessed, depth + 1);
      }
      catch (Throwable t) {
        log(depth, field, "failed: " + t.getMessage());
      }
    }
  }


  public static void bestEffortToCloseAndUnmap(@NotNull Object storage) throws Exception {
    bestEffortToCloseAndUnmap("", storage, new HashSet<>(), 0);
  }

  private static void bestEffortToCloseAndUnmap(@NotNull String fieldName,
                                                @NotNull Object value,
                                                @NotNull Set<Object> alreadyProcessed,
                                                int depth) throws Exception {
    if (isUntouchable(value)) {
      return;
    }
    if (alreadyProcessed.contains(value)) {
      return;
    }
    alreadyProcessed.add(value);

    if (value instanceof Unmappable) {
      log(depth, fieldName, "closeAndUnmap()");
      ((Unmappable)value).closeAndUnsafelyUnmap();
      return;
    }

    if (value instanceof Iterable<?>) {
      log(depth, fieldName, "iterate and dive deeper...");
      int i = 0;
      for (Object nested : (Iterable<?>)value) {
        bestEffortToCloseAndUnmap("[" + i + "]", nested, alreadyProcessed, depth + 1);
        i++;
      }
      return;
    }
    //MAYBE RC: iterate Object[] also?

    //Assume everything else is 'compound' storage that _may_ hold 'raw' underlying storage(s)
    // somewhere deeper:
    if (value instanceof AutoCloseable) {
      log(depth, fieldName, "close() and dive deeper...");
      ((AutoCloseable)value).close();
    }
    else if (value instanceof Disposable) {
      log(depth, fieldName, "dispose() and dive deeper...");
      Disposer.dispose((Disposable)value);
    }
    else {
      log(depth, fieldName, "just dive deeper...");
    }

    Field[] fields = collectAllFields(value);
    for (Field field : fields) {

      if (Modifier.isStatic(field.getModifiers())
          || field.isSynthetic()
          || field.getType().isPrimitive()) {
        //log(depth + 1, field, "ignore");
        continue;
      }

      try {
        field.setAccessible(true);
        Object fieldValue = field.get(value);
        if (fieldValue == null) {
          //log(depth + 1, field, "ignore null");
          continue;
        }

        bestEffortToCloseAndUnmap(field.getName(), fieldValue, alreadyProcessed, depth + 1);
      }
      catch (Throwable t) {
        log(depth, field, "failed: " + t.getMessage());
      }
    }
  }


  private static Field[] collectAllFields(@NotNull Object value) {
    //RC: collect through all the hierarchy seems too dangerous
    //return ReflectionUtil.collectFields(value.getClass()).toArray(Field[]::new);
    return value.getClass().getDeclaredFields();
  }


  /** There are nodes we definitely don't want to dive into */
  private static boolean isUntouchable(@Nullable Object value) {
    if (value == null) {
      return true;
    }

    if (value instanceof ByteBuffer) {
      //every BBuffer we meet could be a mapped buffer, which was already unmapped by one of the .closeXXX()
      // methods before -- so don't touch it even with a stick.
      // Even set.contains(byteBuffer) could crash JVM: it calls value.hashCode() which for
      // ByteBuffer scans through its content
      return true;
    }

    if (value instanceof FilePageCacheLockFree) {
      //don't close app-global cache
      return true;
    }

    //just a few most frequently met types that 100% not worth to dive into
    if (value instanceof Number
        || value instanceof Path
        || value instanceof File
        || value instanceof Map
        || value instanceof Lock
        || value instanceof ReadWriteLock
        || value instanceof Executor
        || value instanceof java.util.logging.Logger
        || value instanceof com.intellij.openapi.diagnostic.Logger
        || value instanceof Condition) {

      return true;
    }

    if (value.getClass().getPackageName().startsWith("io.opentelemetry")) {
      //don't mess with OTel internals
      return true;
    }

    if (value.getClass().equals(Object.class)) {//no harm, but useless
      return true;
    }

    return false;
  }


  private static void log(int depth,
                          Field field,
                          String message) {
    log(repeat(" ", depth) + "." + field.getName() + "(" + field.getType().getSimpleName() + ")" + ": " + message);
  }

  private static void log(int depth,
                          String fieldName,
                          String message) {
    log(repeat(" ", depth) + "." + fieldName + ": " + message);
  }

  private static void log(String message) {
    //System.out.println(message);
  }

  @NotNull
  public static String randomString(final Random rnd,
                                    final int size) {
    final char[] chars = new char[size];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
    }
    return new String(chars);
  }
}
