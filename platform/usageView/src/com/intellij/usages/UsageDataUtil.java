// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class UsageDataUtil {
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
