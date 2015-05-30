/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
class PackageDirectoryCache {
  private final RootIndex myRootIndex;
  private final MultiMap<String, VirtualFile> myRootsByPackagePrefix;
  private final Map<String, List<VirtualFile>> myDirectoriesByPackageNameCache = ContainerUtil.newConcurrentMap();
  private final Set<String> myNonExistentPackages = ContainerUtil.newConcurrentSet();
  @SuppressWarnings("UnusedDeclaration")
  private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      myNonExistentPackages.clear();
    }
  });

  PackageDirectoryCache(RootIndex rootIndex, MultiMap<String, VirtualFile> rootsByPackagePrefix) {
    myRootIndex = rootIndex;
    myRootsByPackagePrefix = rootsByPackagePrefix;
  }

  @NotNull
  List<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName) {
    List<VirtualFile> result = myDirectoriesByPackageNameCache.get(packageName);
    if (result == null) {
      if (myNonExistentPackages.contains(packageName)) return Collections.emptyList();

      result = ContainerUtil.newSmartList();

      if (StringUtil.isNotEmpty(packageName) && !StringUtil.startsWithChar(packageName, '.')) {
        int i = packageName.lastIndexOf('.');
        while (true) {
          String shortName = packageName.substring(i + 1);
          String parentPackage = i > 0 ? packageName.substring(0, i) : "";
          for (VirtualFile parentDir : getDirectoriesByPackageName(parentPackage)) {
            VirtualFile child = parentDir.findChild(shortName);
            if (child != null && child.isDirectory() && myRootIndex.getInfoForFile(child).isInProject()
                && packageName.equals(myRootIndex.getPackageName(child))) {
              result.add(child);
            }
          }
          if (i < 0) break;
          i = packageName.lastIndexOf('.', i - 1);
        }
      }

      for (VirtualFile file : myRootsByPackagePrefix.get(packageName)) {
        if (file.isDirectory()) {
          result.add(file);
        }
      }

      if (!result.isEmpty()) {
        myDirectoriesByPackageNameCache.put(packageName, result);
      } else {
        myNonExistentPackages.add(packageName);
      }
    }

    return result;
  }

}
