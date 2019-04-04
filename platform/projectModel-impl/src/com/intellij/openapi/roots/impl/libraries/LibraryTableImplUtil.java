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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *  @author dsl
 */
public class LibraryTableImplUtil {
  @NonNls public static final String MODULE_LEVEL = "module";

  private LibraryTableImplUtil() {
  }

  @NotNull
  public static Library loadLibrary(@NotNull Element rootElement, @NotNull RootModelImpl rootModel) throws InvalidDataException {
    final List children = rootElement.getChildren(LibraryImpl.ELEMENT);
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

    if (!(library instanceof LibraryImpl)) return false;

    Module module = ((LibraryImpl)library).getModule();
    if (module == null) return false;
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() == library) {
        return true;
      }
    }
    return false;
  }
}
