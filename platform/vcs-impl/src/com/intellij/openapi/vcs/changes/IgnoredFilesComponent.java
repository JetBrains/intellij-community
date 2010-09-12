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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class IgnoredFilesComponent {
  private final Project myProject;
  private final Set<IgnoredFileBean> myFilesToIgnore;

  public IgnoredFilesComponent(final Project project) {
    myProject = project;
    myFilesToIgnore = new LinkedHashSet<IgnoredFileBean>();

    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(List<? extends VFileEvent> events) {}

      public void after(List<? extends VFileEvent> events) {
        resetCaches();
      }
    });
  }

  public void add(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public void clear() {
    synchronized (myFilesToIgnore) {
      myFilesToIgnore.clear();
    }
  }
  public boolean isEmpty() {
    synchronized (myFilesToIgnore) {
      return myFilesToIgnore.isEmpty();
    }
  }

  public void set(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      myFilesToIgnore.clear();
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    synchronized(myFilesToIgnore) {
      return myFilesToIgnore.toArray(new IgnoredFileBean[myFilesToIgnore.size()]);
    }
  }

  private void resetCaches() {
    synchronized (myFilesToIgnore) {
      for (IgnoredFileBean bean : myFilesToIgnore) {
        bean.resetCache();
      }
    }
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    synchronized(myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) return false;

      for(IgnoredFileBean bean: myFilesToIgnore) {
        if (bean.matchesFile(file)) return true;
      }
      return false;
    }
  }
}
