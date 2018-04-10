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
package com.intellij.usages.impl.rules;

import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageFilteringRule;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ReadAccessFilteringRule implements UsageFilteringRule{
  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
    if (usage instanceof ReadWriteAccessUsage) {
      final ReadWriteAccessUsage readWriteAccessUsage = (ReadWriteAccessUsage)usage;
      final boolean isForReadingOnly = readWriteAccessUsage.isAccessedForReading() && !readWriteAccessUsage.isAccessedForWriting();
      return !isForReadingOnly;
    }
    return true;
  }
}
