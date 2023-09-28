// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.pagecache.PagedStorage;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

/**
 *
 */
public class StorageTestingUtils {
  /**
   * Emulates scenario there underlying storage(s) was closed without invoking storage.close() method -- as-if
   * app was kill-9-ed. This is just an emulation -- it doesn't replicate all the details of actual kill-9 --
   * but re-opened storage most likely will have 'not closed properly' state, so appropriate actions could be
   * tested
   */
  public static void emulateImproperClose(@NotNull AutoCloseable storage) throws Exception {
    //It is hard to test 'improperly closed' behaviour of storages because storages APIs are intentionally
    // designed to prohibit 'improperly closed' scenarios. E.g., in most cases, one can't re-open storage
    // that wasn't closed -- all raw storages (MMappedFileStorage, PagedFileStorage, etc) keep track of
    // opened files, and prohibit re-opening of the file that is already opened and not yet closed.
    //
    // The real-life scenario for 'improperly closed' is unexpected app termination -- kill or crash. Such
    // scenarios could be emulated (see StressTestUtil), but it is quite a heavy-weight testing approach.
    //
    // Here is a more light-weight approach to create 'improperly closed' state: we use reflection to
    // find the ~deepest underlying (wrapped) storages, and close them -- without closing top-level storage.
    // Now one _can_ re-open the same file, and got storage in 'improperly closed' state
    Field[] fields = storage.getClass().getDeclaredFields();
    for (Field field : fields) {
      field.setAccessible(true);
      Object fieldValue = field.get(storage);

      //'raw' storages:
      if (fieldValue instanceof MMappedFileStorage) {
        ((MMappedFileStorage)fieldValue).close();
      }
      else if (fieldValue instanceof MappedFileStorageHelper) {
        ((MappedFileStorageHelper)fieldValue).close();
      }
      else if (fieldValue instanceof PagedStorage) {
        ((PagedStorage)fieldValue).close();
      }
      else if (fieldValue instanceof PagedFileStorage) {
        ((PagedFileStorage)fieldValue).close();
      }
      //Assume every other AutoCloseable is 'compound' storage that _may_ hold 'raw' underlying
      // storage somewhere deeper:
      else if (AutoCloseable.class.isAssignableFrom(field.getType())) {
        emulateImproperClose((AutoCloseable)fieldValue);
      }
    }
  }
}
