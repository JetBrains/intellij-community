/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an entry in the classpath of a module (as shown in the "Order/Export" page
 * of the module configuration dialog).
 *
 * @author dsl
 */
@ApiStatus.NonExtendable
public interface OrderEntry extends Synthetic, Comparable<OrderEntry> {
  /**
   * The empty array of order entries which can be reused to avoid unnecessary allocations.
   */
  OrderEntry[] EMPTY_ARRAY = new OrderEntry[0];

  /**
   * Returns the list of root {@link VirtualFile}s of the given type for this entry.
   * @deprecated the meaning of this method is unclear. 
   * If this instance represents dependency on a library or an SDK, use {@link LibraryOrSdkOrderEntry#getRootFiles(OrderRootType)} instead.
   * In other cases, use {@link OrderEnumerator} and specify what files from dependencies of a module you want to get.
   */
  @Deprecated
  VirtualFile @NotNull [] getFiles(@NotNull OrderRootType type);

  /**
   * Returns the list of roots of the given type for this entry.
   *
   * @deprecated the meaning of this method is unclear.
   * If this instance represents dependency on a library or an SDK, use {@link LibraryOrSdkOrderEntry#getRootUrls(OrderRootType)} instead.
   * In other cases, use {@link OrderEnumerator} and specify what files from dependencies of a module you want to get.
   */
  @Deprecated
  String @NotNull [] getUrls(@NotNull OrderRootType rootType);

  /**
   * Returns the user-visible name of this OrderEntry.
   *
   * @return name of this OrderEntry to be shown to user.
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName();

  /**
   * Checks whether this order entry is invalid for some reason. Note that entry being valid
   * does not necessarily mean that all its roots are valid.
   *
   * @return true if entry is valid, false otherwise.
   */
  boolean isValid();

  /**
   * Returns the module to which the entry belongs.
   *
   * @return the module instance.
   */
  @NotNull
  Module getOwnerModule();

  /**
   * Accepts the specified order entries visitor.
   *
   * @param policy       the visitor to accept.
   * @param initialValue the default value to be returned by the visit process.
   * @return the value returned by the visitor.
   */
  <R> R accept(@NotNull RootPolicy<R> policy, @Nullable R initialValue);
}
