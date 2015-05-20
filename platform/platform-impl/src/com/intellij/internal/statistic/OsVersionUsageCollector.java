/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
class OsVersionUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    UsageDescriptor descriptor = null;

    if (SystemInfo.isUnix && !SystemInfo.isMac) {
      String releaseName = SystemInfo.getUnixReleaseName();
      String releaseVersion = SystemInfo.getUnixReleaseVersion();
      if (releaseName != null && releaseVersion != null) {
        descriptor = new UsageDescriptor(SystemInfo.OS_NAME + " " + releaseName + " " + releaseVersion, 1);
      }
    }

    if (descriptor == null) {
      descriptor = new UsageDescriptor(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION, 1);
    }

    return Collections.singleton(descriptor);
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.os.version");
  }
}
