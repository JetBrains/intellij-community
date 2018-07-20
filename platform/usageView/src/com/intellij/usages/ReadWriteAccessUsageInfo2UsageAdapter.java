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
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage{
  private final byte initialRwLevel; // 0,1,2 -> R, W, RW
  private byte myRwLevel;

  public ReadWriteAccessUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, final boolean accessedForReading, final boolean accessedForWriting) {
    super(usageInfo);
    initialRwLevel = myRwLevel = (byte)((accessedForReading ? 1 : 0) | (accessedForWriting ? 2 : 0));
    computeIcon();
  }

  private static class RW {
    private static final Icon[] ICONS = {
      PlatformIcons.VARIABLE_READ_ACCESS,
      PlatformIcons.VARIABLE_READ_ACCESS,
      PlatformIcons.VARIABLE_WRITE_ACCESS,
      PlatformIcons.VARIABLE_RW_ACCESS};
  }

  private void computeIcon() {
    myIcon = RW.ICONS[myRwLevel];
  }

  @Override
  public boolean merge(@NotNull MergeableUsage other) {
    boolean merged = super.merge(other);
    if (merged && other instanceof ReadWriteAccessUsageInfo2UsageAdapter) {
      myRwLevel |= ((ReadWriteAccessUsageInfo2UsageAdapter)other).myRwLevel;
      computeIcon();
    }
    return merged;
  }

  @Override
  public void reset() {
    super.reset();
    myRwLevel = initialRwLevel;
    computeIcon();
  }

  @Override
  public boolean isAccessedForWriting() {
    return BitUtil.isSet(myRwLevel, 2);
  }

  @Override
  public boolean isAccessedForReading() {
    return BitUtil.isSet(myRwLevel, 1);
  }
}
