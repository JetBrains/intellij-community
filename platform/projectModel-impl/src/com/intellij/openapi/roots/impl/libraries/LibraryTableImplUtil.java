// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
@ApiStatus.Internal
public final class LibraryTableImplUtil {
  @NonNls public static final String MODULE_LEVEL = "module";

  private LibraryTableImplUtil() {
  }

  public static boolean isValidLibrary(@NotNull Library library) {
    LibraryTable table = library.getTable();
    if (table != null) {
      String name = library.getName();
      return name != null && table.getLibraryByName(name) == library;
    }

    if (!(library instanceof LibraryEx)) return false;

    Module module = ((LibraryEx)library).getModule();
    if (module == null || module.isDisposed()) return false;
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() == library) {
        return true;
      }
    }
    return false;
  }
}
