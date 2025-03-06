// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class MemoryUtil {

  @Nullable
  public static Long getUnusedMemory() {
    if (!SystemInfo.isMac) return null;
    IntByReference pageSizeRef = new IntByReference();
    if (LibC.INSTANCE.host_page_size(LibC.INSTANCE.mach_host_self(), pageSizeRef) == -1) {
      return null;
    }
    long pageSize = pageSizeRef.getValue();
    VmStatistics64 stats = new VmStatistics64();
    IntByReference count = new IntByReference(stats.size() / 4);
    if(LibC.INSTANCE.host_statistics64(LibC.INSTANCE.mach_host_self(), LibC.HOST_VM_INFO64, stats, count) == -1){
      return null;
    }

    return (stats.free_count + stats.inactive_count) * pageSize;
  }

  private interface LibC extends Library {
    LibC INSTANCE = Native.load(LibC.class);

    int HOST_VM_INFO64 = 2;

    int host_statistics64(int host, int flavor, Structure stats, IntByReference count);

    int host_page_size(int host, IntByReference pageSize);

    int mach_host_self();
  }

  public static class VmStatistics64 extends Structure {
    public int free_count;
    public int active_count;
    public int inactive_count;
    public int wire_count;
    public int zero_fill_count;
    public int reactivations;
    public int pageins;
    public int pageouts;
    public int faults;
    public int cow_faults;
    public int lookups;
    public int hits;
    public int purges;
    public int speculative_count;

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList(
        "free_count", "active_count", "inactive_count", "wire_count",
        "zero_fill_count", "reactivations", "pageins", "pageouts",
        "faults", "cow_faults", "lookups", "hits", "purges", "speculative_count"
      );
    }
  }
}
