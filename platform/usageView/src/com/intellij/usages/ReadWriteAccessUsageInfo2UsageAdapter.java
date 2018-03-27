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
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage{
  private final boolean myAccessedForReading;
  private final boolean myAccessedForWriting;

  public ReadWriteAccessUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, final boolean accessedForReading, final boolean accessedForWriting) {
    super(usageInfo);
    myAccessedForReading = accessedForReading;
    myAccessedForWriting = accessedForWriting;
    if (myAccessedForReading && myAccessedForWriting) {
      myIcon = PlatformIcons.VARIABLE_RW_ACCESS;
    }
    else if (myAccessedForWriting) {
      myIcon = PlatformIcons.VARIABLE_WRITE_ACCESS;           // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
    }
    else if (myAccessedForReading){
      myIcon = PlatformIcons.VARIABLE_READ_ACCESS;            // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
    }
  }

  @Override
  public boolean isAccessedForWriting() {
    return myAccessedForWriting;
  }

  @Override
  public boolean isAccessedForReading() {
    return myAccessedForReading;
  }



}
