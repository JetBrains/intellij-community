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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
class NonProjectDirectoryInfo extends DirectoryInfo {
  public static final NonProjectDirectoryInfo IGNORED = new NonProjectDirectoryInfo("ignored") {
    @Override
    public boolean isIgnored() {
      return true;
    }
  };
  public static final NonProjectDirectoryInfo EXCLUDED = new NonProjectDirectoryInfo("excluded from project", true);
  public static final NonProjectDirectoryInfo NOT_UNDER_PROJECT_ROOTS = new NonProjectDirectoryInfo("not under project roots");
  public static final NonProjectDirectoryInfo INVALID = new NonProjectDirectoryInfo("invalid");
  public static final NonProjectDirectoryInfo NOT_SUPPORTED_VIRTUAL_FILE_IMPLEMENTATION = new NonProjectDirectoryInfo("not supported VirtualFile implementation");
  private final String myDebugName;

  private NonProjectDirectoryInfo(String debugName) {
    this(debugName, false);
  }

  private NonProjectDirectoryInfo(String debugName, boolean isExcluded) {
    super(null, null, null, null, false, false, isExcluded, 0);
    myDebugName = debugName;
  }

  @Override
  public boolean isInProject() {
    return false;
  }

  @NotNull
  @Override
  public OrderEntry[] getOrderEntries() {
    return OrderEntry.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule) {
    return null;
  }

  @NotNull
  @Override
  List<OrderEntry> findAllOrderEntriesWithOwnerModule(@NotNull Module ownerModule) {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "DirectoryInfo: " + myDebugName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
