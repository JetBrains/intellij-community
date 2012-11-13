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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;


public final class DirectoryInfo {
  private Module module; // module to which content it belongs or null
  private VirtualFile libraryClassRoot; // class root in library
  private VirtualFile contentRoot;
  private VirtualFile sourceRoot;

  private static final byte TEST_SOURCE_FLAG = 1; // (makes sense only if MODULE_SOURCE_FLAG is set)
  private static final byte LIBRARY_SOURCE_FLAG = 2; // set if it's a directory with sources of some library
  private static final byte MODULE_SOURCE_FLAG = 4; // set if files in this directory belongs to sources of the module (if field 'module' is not null)

  @MagicConstant(flags = {TEST_SOURCE_FLAG, LIBRARY_SOURCE_FLAG, MODULE_SOURCE_FLAG})
  private byte sourceFlag;

  /**
   * orderEntry to (classes of) which a directory belongs
   */
  private OrderEntry[] orderEntries;

  public DirectoryInfo() {
  }

  @TestOnly
  @SuppressWarnings({"unchecked"})
  public boolean equals(Object o) {
    assert ApplicationManager.getApplication().isUnitTestMode() : "DirectoryInfo.equals should only be used in tests";

    if (this == o) return true;
    if (!(o instanceof DirectoryInfo)) return false;

    final DirectoryInfo info = (DirectoryInfo)o;

    if (sourceFlag != info.sourceFlag) return false;
    if (getModule() != null ? !getModule().equals(info.getModule()) : info.getModule() != null) return false;
    if (orderEntries != null ? !new HashSet(Arrays.asList(orderEntries)).equals(new HashSet(Arrays.asList(info.orderEntries))) : info.orderEntries != null) return false;
    if (!Comparing.equal(getLibraryClassRoot(), info.getLibraryClassRoot())) return false;
    if (!Comparing.equal(getContentRoot(), info.getContentRoot())) return false;
    if (!Comparing.equal(getSourceRoot(), info.getSourceRoot())) return false;

    return true;
  }

  public int hashCode() {
    throw new UnsupportedOperationException("DirectoryInfo shall not be used as a key to HashMap");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + getModule() +
           ", isInModuleSource=" + isInModuleSource() +
           ", isTestSource=" + isTestSource() +
           ", isInLibrarySource=" + isInLibrarySource() +
           ", libraryClassRoot=" + getLibraryClassRoot() +
           ", contentRoot=" + getContentRoot() +
           ", sourceRoot=" + getSourceRoot() +
           "}";
  }

  @NotNull
  public OrderEntry[] getOrderEntries() {
    return orderEntries == null ? OrderEntry.EMPTY_ARRAY : orderEntries;
  }

  @Nullable
  OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule) {
    OrderEntry[] entries = orderEntries;
    if (entries == null) {
      return null;
    }
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
    OrderEntry[] entries = orderEntries;
    if (entries == null) {
      return Collections.emptyList();
    }
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
      public int compareTo(OrderEntry o) {
        throw new IncorrectOperationException();
      }

      @Override
      public boolean isSynthetic() {
        throw new IncorrectOperationException();
      }
    };
  }

  // orderEntries must be sorted BY_OWNER_MODULE
  void addOrderEntries(@NotNull OrderEntry[] orderEntries,
                       @Nullable final DirectoryInfo parentInfo,
                       @Nullable final OrderEntry[] oldParentEntries) {
    if (orderEntries.length == 0) {
      this.orderEntries = null;
    }
    else if (this.orderEntries == null) {
      this.orderEntries = orderEntries;
    }
    else if (parentInfo != null && oldParentEntries == this.orderEntries) {
      this.orderEntries = parentInfo.orderEntries;
    }
    else {
      this.orderEntries = mergeWith(orderEntries);
    }
  }

  // entries must be sorted BY_OWNER_MODULE
  @NotNull
  private OrderEntry[] mergeWith(@NotNull OrderEntry[] entries) {
    OrderEntry[] orderEntries = this.orderEntries;
    OrderEntry[] result = new OrderEntry[orderEntries.length + entries.length];
    int i=0;
    int j=0;
    // remove equals entries in the process
    int o = 0;
    while (i != orderEntries.length || j != entries.length) {
      OrderEntry m = i != orderEntries.length && (j == entries.length || BY_OWNER_MODULE.compare(orderEntries[i], entries[j]) < 0)
                     ? orderEntries[i++]
                     : entries[j++];
      if (o==0 || !m.equals(result[o - 1])) {
        result[o++] = m;
      }
    }
    if (o != result.length) {
      result = ArrayUtil.realloc(result, o, ORDER_ENTRY_ARRAY_FACTORY);
    }
    return result;
  }

  private static final ArrayFactory<OrderEntry> ORDER_ENTRY_ARRAY_FACTORY = new ArrayFactory<OrderEntry>() {
    @Override
    public OrderEntry[] create(int count) {
      return count == 0 ? OrderEntry.EMPTY_ARRAY : new OrderEntry[count];
    }
  };

  public static final Comparator<OrderEntry> BY_OWNER_MODULE = new Comparator<OrderEntry>() {
    @Override
    public int compare(OrderEntry o1, OrderEntry o2) {
      String name1 = o1.getOwnerModule().getName();
      String name2 = o2.getOwnerModule().getName();
      return name1.compareTo(name2);
    }
  };

  public void setInternedOrderEntries(@NotNull OrderEntry[] internedOrderEntries) {
    orderEntries = internedOrderEntries;
  }

  public VirtualFile getSourceRoot() {
    return sourceRoot;
  }
  public boolean hasSourceRoot() {
    return getSourceRoot() != null;
  }

  public VirtualFile getLibraryClassRoot() {
    return libraryClassRoot;
  }
  public boolean hasLibraryClassRoot() {
    return getLibraryClassRoot() != null;
  }

  public VirtualFile getContentRoot() {
    return contentRoot;
  }

  public boolean isInModuleSource() {
    return BitUtil.isSet(sourceFlag, MODULE_SOURCE_FLAG);
  }

  public void setInModuleSource(boolean inModuleSource) {
    sourceFlag = (byte)BitUtil.set(sourceFlag, MODULE_SOURCE_FLAG, inModuleSource);
  }

  public boolean isTestSource() {
    return BitUtil.isSet(sourceFlag, TEST_SOURCE_FLAG);
  }

  public void setTestSource(boolean testSource) {
    sourceFlag = (byte)BitUtil.set(sourceFlag, TEST_SOURCE_FLAG, testSource);
  }

  public boolean isInLibrarySource() {
    return BitUtil.isSet(sourceFlag, LIBRARY_SOURCE_FLAG);
  }

  public void setInLibrarySource(boolean inLibrarySource) {
    sourceFlag = (byte)BitUtil.set(sourceFlag, LIBRARY_SOURCE_FLAG, inLibrarySource);
  }

  public Module getModule() {
    return module;
  }

  public void setModule(Module module) {
    this.module = module;
  }

  public void setLibraryClassRoot(@NotNull VirtualFile libraryClassRoot) {
    this.libraryClassRoot = libraryClassRoot;
  }

  public void setContentRoot(VirtualFile contentRoot) {
    this.contentRoot = contentRoot;
  }

  public void setSourceRoot(@NotNull VirtualFile sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  @TestOnly
  public void assertConsistency() {
    OrderEntry[] entries = getOrderEntries();
    for (int i=1; i<entries.length; i++) {
      assert BY_OWNER_MODULE.compare(entries[i-1], entries[i]) <= 0;
    }
  }
}
