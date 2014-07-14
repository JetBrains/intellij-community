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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class DirectoryInfo {
  public static final int MAX_ROOT_TYPE_ID = (1 << (Byte.SIZE - 2)) - 1;
  private final Module module; // module to which content it belongs or null
  private final VirtualFile libraryClassRoot; // class root in library
  private final VirtualFile contentRoot;
  private final VirtualFile sourceRoot;

  private static final byte MODULE_SOURCE_FLAG = 1; // set if files in this directory belongs to sources of the module (if field 'module' is not null)
  private static final byte LIBRARY_SOURCE_FLAG = 2; // set if it's a directory with sources of some library
  private final byte sourceRootTypeData;//two least significant bits are used for MODULE_SOURCE_FLAG and LIBRARY_SOURCE_FLAG, the remaining bits store module root type id (source/tests/resources/...)

  DirectoryInfo(Module module,
                VirtualFile contentRoot,
                VirtualFile sourceRoot,
                VirtualFile libraryClassRoot,
                byte sourceRootTypeData) {
    this.module = module;
    this.libraryClassRoot = libraryClassRoot;
    this.contentRoot = contentRoot;
    this.sourceRoot = sourceRoot;
    this.sourceRootTypeData = sourceRootTypeData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DirectoryInfo info = (DirectoryInfo)o;

    return sourceRootTypeData == info.sourceRootTypeData &&
           Comparing.equal(contentRoot, info.contentRoot) &&
           Comparing.equal(libraryClassRoot, info.libraryClassRoot) &&
           Comparing.equal(module, info.module) &&
           Arrays.equals(getOrderEntries(), info.getOrderEntries()) &&
           Comparing.equal(sourceRoot, info.sourceRoot);
  }

  @Override
  public int hashCode() {
    int result = module != null ? module.hashCode() : 0;
    result = 31 * result + (libraryClassRoot != null ? libraryClassRoot.hashCode() : 0);
    result = 31 * result + (contentRoot != null ? contentRoot.hashCode() : 0);
    result = 31 * result + (sourceRoot != null ? sourceRoot.hashCode() : 0);
    result = 31 * result + (int)sourceRootTypeData;
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + getModule() +
           ", isInModuleSource=" + isInModuleSource() +
           ", rootTypeId=" + getSourceRootTypeId() +
           ", isInLibrarySource=" + isInLibrarySource() +
           ", libraryClassRoot=" + getLibraryClassRoot() +
           ", contentRoot=" + getContentRoot() +
           ", sourceRoot=" + getSourceRoot() +
           ", orderEntries=" + Arrays.toString(getOrderEntries()) +
           "}";
  }

  @NotNull
  public abstract OrderEntry[] getOrderEntries();

  @Nullable
  OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule) {
    OrderEntry[] entries = getOrderEntries();
    if (entries.length < 10) {
      for (OrderEntry entry : entries) {
        if (entry.getOwnerModule() == ownerModule) return entry;
      }
      return null;
    }
    int index = Arrays.binarySearch(entries, createFakeOrderEntry(ownerModule), BY_OWNER_MODULE);
    return index < 0 ? null : entries[index];
  }

  @NotNull
  List<OrderEntry> findAllOrderEntriesWithOwnerModule(@NotNull Module ownerModule) {
    OrderEntry[] entries = getOrderEntries();
    if (entries.length == 1) {
      OrderEntry entry = entries[0];
      return entry.getOwnerModule() == ownerModule ? Arrays.asList(entries) : Collections.<OrderEntry>emptyList();
    }
    int index = Arrays.binarySearch(entries, createFakeOrderEntry(ownerModule), BY_OWNER_MODULE);
    if (index < 0) {
      return Collections.emptyList();
    }
    int firstIndex = index;
    while (firstIndex-1 >= 0 && entries[firstIndex-1].getOwnerModule() == ownerModule) {
      firstIndex--;
    }
    int lastIndex = index+1;
    while (lastIndex < entries.length && entries[lastIndex].getOwnerModule() == ownerModule) {
      lastIndex++;
    }

    OrderEntry[] subArray = new OrderEntry[lastIndex - firstIndex];
    System.arraycopy(entries, firstIndex, subArray, 0, lastIndex - firstIndex);

    return Arrays.asList(subArray);
  }

  @NotNull
  private static OrderEntry createFakeOrderEntry(@NotNull final Module ownerModule) {
    return new OrderEntry() {
      @NotNull
      @Override
      public VirtualFile[] getFiles(OrderRootType type) {
        throw new IncorrectOperationException();
      }

      @NotNull
      @Override
      public String[] getUrls(OrderRootType rootType) {
        throw new IncorrectOperationException();
      }

      @NotNull
      @Override
      public String getPresentableName() {
        throw new IncorrectOperationException();
      }

      @Override
      public boolean isValid() {
        throw new IncorrectOperationException();
      }

      @NotNull
      @Override
      public Module getOwnerModule() {
        return ownerModule;
      }

      @Override
      public <R> R accept(RootPolicy<R> policy, @Nullable R initialValue) {
        throw new IncorrectOperationException();
      }

      @Override
      public int compareTo(@NotNull OrderEntry o) {
        throw new IncorrectOperationException();
      }

      @Override
      public boolean isSynthetic() {
        throw new IncorrectOperationException();
      }
    };
  }

  public static final Comparator<OrderEntry> BY_OWNER_MODULE = new Comparator<OrderEntry>() {
    @Override
    public int compare(OrderEntry o1, OrderEntry o2) {
      String name1 = o1.getOwnerModule().getName();
      String name2 = o2.getOwnerModule().getName();
      return name1.compareTo(name2);
    }
  };

  @Nullable
  public VirtualFile getSourceRoot() {
    return sourceRoot;
  }

  public VirtualFile getLibraryClassRoot() {
    return libraryClassRoot;
  }

  public boolean hasLibraryClassRoot() {
    return getLibraryClassRoot() != null;
  }

  @Nullable
  public VirtualFile getContentRoot() {
    return contentRoot;
  }

  public boolean isInModuleSource() {
    return BitUtil.isSet(sourceRootTypeData, MODULE_SOURCE_FLAG);
  }

  public boolean isInLibrarySource() {
    return BitUtil.isSet(sourceRootTypeData, LIBRARY_SOURCE_FLAG);
  }

  public Module getModule() {
    return module;
  }

  @TestOnly
  void assertConsistency() {
    OrderEntry[] entries = getOrderEntries();
    for (int i=1; i<entries.length; i++) {
      assert BY_OWNER_MODULE.compare(entries[i-1], entries[i]) <= 0;
    }
  }

  public int getSourceRootTypeId() {
    return sourceRootTypeData >> 2;
  }

  public static int createSourceRootTypeData(boolean isInModuleSources, boolean isInLibrarySource, int moduleSourceRootTypeId) {
    if (moduleSourceRootTypeId > MAX_ROOT_TYPE_ID) {
      throw new IllegalArgumentException("Module source root type id " + moduleSourceRootTypeId + " exceeds the maximum allowable value (" + MAX_ROOT_TYPE_ID + ")");
    }
    return (isInModuleSources ? MODULE_SOURCE_FLAG : 0) | (isInLibrarySource ? LIBRARY_SOURCE_FLAG : 0) | moduleSourceRootTypeId << 2;
  }
}
