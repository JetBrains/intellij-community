// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.util.List;

/**
 *  @author dsl
 */
@ApiStatus.Internal
public final class LibraryTableImplUtil {
  @NonNls public static final String MODULE_LEVEL = "module";

  private LibraryTableImplUtil() {
  }

  @NotNull
  public static Library loadLibrary(@NotNull Element rootElement, @NotNull RootModelImpl rootModel) throws InvalidDataException {
    final List children = rootElement.getChildren(JpsLibraryTableSerializer.LIBRARY_TAG);
    if (children.size() != 1) throw new InvalidDataException();
    Element element = (Element)children.get(0);
    return new LibraryImpl(null, element, rootModel);
  }

  @NotNull
  public static Library createModuleLevelLibrary(@Nullable String name, PersistentLibraryKind kind, @NotNull RootModelImpl rootModel,
                                                 @Nullable ProjectModelExternalSource externalSource) {
    return new LibraryImpl(name, kind, null, rootModel, externalSource);
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
