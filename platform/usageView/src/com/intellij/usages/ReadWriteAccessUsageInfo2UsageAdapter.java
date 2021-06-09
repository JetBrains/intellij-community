// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage{
  private final byte initialRwLevel; // 0,1,2 -> R, W, RW
  private volatile byte myRwLevel;

  public ReadWriteAccessUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, final boolean accessedForReading, final boolean accessedForWriting) {
    super(usageInfo);
    myRwLevel = initialRwLevel = (byte)((accessedForReading ? 1 : 0) | (accessedForWriting ? 2 : 0));
  }

  private static class RW {
    private static final Icon[] ICONS = {
      PlatformIcons.VARIABLE_READ_ACCESS,
      PlatformIcons.VARIABLE_READ_ACCESS,
      PlatformIcons.VARIABLE_WRITE_ACCESS,
      PlatformIcons.VARIABLE_RW_ACCESS};
  }

  @Override
  protected @Nullable Icon computeIcon() {
    return RW.ICONS[myRwLevel];
  }

  @Override
  public boolean merge(@NotNull MergeableUsage other) {
    boolean merged = super.merge(other);
    if (merged && other instanceof ReadWriteAccessUsageInfo2UsageAdapter) {
      int newLevel = myRwLevel | ((ReadWriteAccessUsageInfo2UsageAdapter)other).myRwLevel;
      myRwLevel = (byte)newLevel;
    }
    return merged;
  }

  @Override
  public void reset() {
    super.reset();
    myRwLevel = initialRwLevel;
  }

  @Override
  public boolean isAccessedForWriting() {
    return BitUtil.isSet(myRwLevel, 2);
  }

  @Override
  public boolean isAccessedForReading() {
    return BitUtil.isSet(myRwLevel, 1);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReadWriteAccessUsageInfo2UsageAdapter adapter = (ReadWriteAccessUsageInfo2UsageAdapter)o;
    return initialRwLevel == adapter.initialRwLevel &&
           myRwLevel == adapter.myRwLevel
      && this.getUsageInfo().equals(((ReadWriteAccessUsageInfo2UsageAdapter)o).getUsageInfo());
  }

  @Override
  public int hashCode() {
    return Objects.hash(initialRwLevel, myRwLevel, getUsageInfo());
  }
}
