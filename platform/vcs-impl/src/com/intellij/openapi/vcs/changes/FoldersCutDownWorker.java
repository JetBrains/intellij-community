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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashSet;
import java.util.Set;

public class FoldersCutDownWorker {
  private final Set<String> myPaths;

  public FoldersCutDownWorker() {
    myPaths = new HashSet<String>();
  }

  public boolean addCurrent(final VirtualFile file) {
    for (String path : myPaths) {
      if (FileUtil.startsWith(file.getPath(), path)) {
        return false;
      }
    }

    myPaths.add(file.getPath());
    return true;
  }
}
