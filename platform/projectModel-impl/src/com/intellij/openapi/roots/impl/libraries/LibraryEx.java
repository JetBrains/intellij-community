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

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *  @author dsl
 */
public interface LibraryEx extends Library {
  Library cloneLibrary(RootModelImpl rootModel);

  List<String> getInvalidRootUrls(OrderRootType type);

  boolean isDisposed();

  @Nullable
  PersistentLibraryKind<?> getKind();

  LibraryProperties getProperties();

  interface ModifiableModelEx extends ModifiableModel {
    void setProperties(LibraryProperties properties);

    LibraryProperties getProperties();

    void setKind(PersistentLibraryKind<?> type);

    PersistentLibraryKind<?> getKind();
  }
}
