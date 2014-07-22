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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public abstract class DirectoryInfoImpl extends DirectoryInfo {
  public static final Comparator<OrderEntry> BY_OWNER_MODULE = new Comparator<OrderEntry>() {
    @Override
    public int compare(OrderEntry o1, OrderEntry o2) {
      String name1 = o1.getOwnerModule().getName();
      String name2 = o2.getOwnerModule().getName();
      return name1.compareTo(name2);
    }
  };
  public static final int MAX_ROOT_TYPE_ID = Byte.MAX_VALUE;
  private final Module module; // module to which content it belongs or null
  private final VirtualFile libraryClassRoot; // class root in library
  private final VirtualFile contentRoot;
  private final VirtualFile sourceRoot;
  private final boolean myInModuleSource;
  private final boolean myInLibrarySource;
  private final boolean myExcluded;
  private final byte mySourceRootTypeId;

  DirectoryInfoImpl(Module module, VirtualFile contentRoot, VirtualFile sourceRoot, VirtualFile libraryClassRoot,
                    boolean inModuleSource, boolean inLibrarySource, boolean isExcluded, int sourceRootTypeId) {
    this.module = module;
    this.libraryClassRoot = libraryClassRoot;
    this.contentRoot = contentRoot;
    this.sourceRoot = sourceRoot;
    myInModuleSource = inModuleSource;
    myInLibrarySource = inLibrarySource;
    myExcluded = isExcluded;
    if (sourceRootTypeId > MAX_ROOT_TYPE_ID) {
      throw new IllegalArgumentException(
        "Module source root type id " + sourceRootTypeId + " exceeds the maximum allowable value (" + MAX_ROOT_TYPE_ID + ")");
    }
    mySourceRootTypeId = (byte)sourceRootTypeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DirectoryInfoImpl info = (DirectoryInfoImpl)o;

    return mySourceRootTypeId == info.mySourceRootTypeId &&
           myInModuleSource == info.myInModuleSource &&
           myInLibrarySource == info.myInLibrarySource &&
           myExcluded == info.myExcluded &&
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
    result = 31 * result + (myInModuleSource ? 1 : 0);
    result = 31 * result + (myInLibrarySource ? 1 : 0);
    result = 31 * result + (myExcluded ? 1 : 0);
    result = 31 * result + (int)mySourceRootTypeId;
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + getModule() +
           ", isInModuleSource=" + isInModuleSource() +
           ", rootTypeId=" + getSourceRootTypeId() +
           ", isInLibrarySource=" + isInLibrarySource() +
           ", isExcludedFromModule=" + isExcluded() +
           ", libraryClassRoot=" + getLibraryClassRoot() +
           ", contentRoot=" + getContentRoot() +
           ", sourceRoot=" + getSourceRoot() +
           ", orderEntries=" + Arrays.toString(getOrderEntries()) +
           "}";
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

  public boolean isInProject() {
    return !isExcluded();
  }

  public boolean isIgnored() {
    return false;
  }

  @Nullable
  public VirtualFile getSourceRoot() {
    return sourceRoot;
  }

  public VirtualFile getLibraryClassRoot() {
    return libraryClassRoot;
  }

  @Nullable
  public VirtualFile getContentRoot() {
    return contentRoot;
  }

  public boolean isInModuleSource() {
    return myInModuleSource;
  }

  public boolean isInLibrarySource() {
    return myInLibrarySource;
  }

  public boolean isExcluded() {
    return myExcluded;
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
    return mySourceRootTypeId;
  }
}
