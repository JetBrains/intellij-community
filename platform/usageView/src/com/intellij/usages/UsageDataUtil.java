/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class UsageDataUtil {
  public static VirtualFile @Nullable [] provideVirtualFileArray(Usage[] usages, UsageTarget[] usageTargets) {
    if (usages == null && usageTargets == null) {
      return null;
    }

    final Set<VirtualFile> result = new THashSet<>();

    if (usages != null) {
      for (Usage usage : usages) {
        if (usage instanceof UsageInFile) {
          VirtualFile file = ((UsageInFile)usage).getFile();
          if (file != null && file.isValid()) {
            result.add(file);
          }
        }

        if (usage instanceof UsageInFiles) {
          VirtualFile[] files = ((UsageInFiles)usage).getFiles();
          for (VirtualFile file : files) {
            if (file.isValid()) {
              result.add(file);
            }
          }
        }
      }
    }

    if (usageTargets != null) {
      for (UsageTarget usageTarget : usageTargets) {
        if (usageTarget.isValid()) {
          final VirtualFile[] files = usageTarget.getFiles();
          if (files != null) {
            ContainerUtil.addAll(result, files);
          }
        }
      }
    }

    return VfsUtilCore.toVirtualFileArray(result);
  }
}
