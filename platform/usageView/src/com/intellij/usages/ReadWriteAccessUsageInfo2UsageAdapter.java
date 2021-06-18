// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage {

  /**
   * Ordinal of {@link Access}
   */
  private final byte myInitialRwAccess;
  private volatile byte myRwAccess;

  public ReadWriteAccessUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, @NotNull Access rwAccess) {
    super(usageInfo);
    myRwAccess = myInitialRwAccess = (byte)rwAccess.ordinal();
  }

  /**
   * @deprecated use {@link #ReadWriteAccessUsageInfo2UsageAdapter(UsageInfo, Access)}
   */
  @Deprecated
  public ReadWriteAccessUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, boolean accessedForReading, boolean accessedForWriting) {
    this(usageInfo, getRwAccess(accessedForReading, accessedForWriting));
  }

  private static class RW {
    /**
     * Mapping from ordinal of {@link Access} to Icon.
     */
    private static final Icon[] ICONS = {
      PlatformIcons.VARIABLE_READ_ACCESS,
      PlatformIcons.VARIABLE_WRITE_ACCESS,
      PlatformIcons.VARIABLE_RW_ACCESS
    };
  }

  @Override
  protected @Nullable Icon computeIcon() {
    return RW.ICONS[myRwAccess];
  }

  @Override
  public boolean merge(@NotNull MergeableUsage other) {
    boolean merged = super.merge(other);
    if (merged && other instanceof ReadWriteAccessUsageInfo2UsageAdapter) {
      Access newRwAccess = mergeRwAccess(rwAccess(), ((ReadWriteAccessUsageInfo2UsageAdapter)other).rwAccess());
      myRwAccess = (byte)newRwAccess.ordinal();
    }
    return merged;
  }

  @Override
  public void reset() {
    super.reset();
    myRwAccess = myInitialRwAccess;
  }

  private @NotNull Access rwAccess() {
    return Access.values()[myRwAccess];
  }

  @Override
  public boolean isAccessedForWriting() {
    return rwAccess() != Access.Read;
  }

  @Override
  public boolean isAccessedForReading() {
    return rwAccess() != Access.Write;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReadWriteAccessUsageInfo2UsageAdapter adapter = (ReadWriteAccessUsageInfo2UsageAdapter)o;
    return myInitialRwAccess == adapter.myInitialRwAccess &&
           myRwAccess == adapter.myRwAccess
           && this.getUsageInfo().equals(((ReadWriteAccessUsageInfo2UsageAdapter)o).getUsageInfo());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myInitialRwAccess, myRwAccess, getUsageInfo());
  }

  private static @NotNull Access mergeRwAccess(@NotNull Access left, @NotNull Access right) {
    return left == right ? left : Access.ReadWrite;
  }

  private static @NotNull Access getRwAccess(boolean accessedForReading, boolean accessedForWriting) {
    if (accessedForReading && accessedForWriting) {
      return Access.ReadWrite;
    }
    else if (accessedForReading) {
      return Access.Read;
    }
    else if (accessedForWriting) {
      return Access.Write;
    }
    else {
      throw new IllegalArgumentException("At least one of 'accessedForReading' or 'accessedForWriting' must be 'true'");
    }
  }
}
