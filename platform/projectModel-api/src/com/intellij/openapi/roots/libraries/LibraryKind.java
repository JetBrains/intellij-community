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
package com.intellij.openapi.roots.libraries;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LibraryKind {
  private final String myKindId;
  private static final Map<String, LibraryKind> ourAllKinds = new HashMap<>();

  /**
   * @param kindId must be unique among all {@link com.intellij.openapi.roots.libraries.LibraryType} and {@link com.intellij.openapi.roots.libraries.LibraryPresentationProvider} implementations
   */
  public LibraryKind(@NotNull @NonNls String kindId) {
    myKindId = kindId;
    LibraryKind kind = ourAllKinds.get(kindId);
    if (kind != null && !(kind instanceof TemporaryLibraryKind)) {
      throw new IllegalArgumentException("Kind " + kindId + " is not unique");
    }
    ourAllKinds.put(kindId, this);
  }

  public final String getKindId() {
    return myKindId;
  }

  @Override
  public String toString() {
    return "LibraryKind:" + myKindId;
  }

  /**
   * @param kindId must be unique among all {@link LibraryType} and {@link LibraryPresentationProvider} implementations
   * @return new {@link LibraryKind} instance
   */
  public static LibraryKind create(@NotNull @NonNls String kindId) {
    return new LibraryKind(kindId);
  }

  /**
   * @deprecated it's better to store instance of {@code LibraryKind} instead of looking it by ID; if you really need to find an instance by
   * its ID, use {@link LibraryKindRegistry#findKindById(String)}
   */
  @Deprecated
  public static LibraryKind findById(String kindId) {
    return LibraryKindRegistry.getInstance().findKindById(kindId);
  }

  static @Nullable LibraryKind findByIdInternal(@Nullable String kindId) {
    return ourAllKinds.get(kindId);
  }

  @ApiStatus.Internal
  public static void unregisterKind(@NotNull LibraryKind kind) {
    ourAllKinds.remove(kind.getKindId());
  }

  @ApiStatus.Internal
  public static void registerKind(@NotNull LibraryKind kind) {
    ourAllKinds.put(kind.getKindId(), kind);
  }
}
