/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.*;

public class IgnoredFilesComponent {
  private final Set<IgnoredFileBean> myFilesToIgnore;
  private final Map<String, IgnoredFileBean> myFilesMap;

  public IgnoredFilesComponent(final Project project, final boolean registerListener) {
    myFilesToIgnore = new LinkedHashSet<IgnoredFileBean>();
    myFilesMap = new HashMap<String, IgnoredFileBean>();

    if (registerListener) {
      project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {
          resetCaches();
        }
      });
    }
  }

  public IgnoredFilesComponent(final IgnoredFilesComponent other) {
    myFilesToIgnore = new LinkedHashSet<IgnoredFileBean>(other.myFilesToIgnore);
    myFilesMap = new HashMap<String, IgnoredFileBean>(other.myFilesMap);
  }

  public void add(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
      addIgnoredFiles(filesToIgnore);
    }
  }

  private void addIgnoredFiles(final IgnoredFileBean... filesToIgnore) {
    for (IgnoredFileBean bean : filesToIgnore) {
      if (IgnoreSettingsType.FILE.equals(bean.getType())) {
        final Project project = bean.getProject();
        final VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
          // if baseDir == null, then nothing will be added to map, but check will still be correct through set
          myFilesMap.put(FilePathsHelper.convertPath(baseDir.getPath(), bean.getPath()), bean);
        }
      }
    }
  }

  public void clear() {
    synchronized (myFilesToIgnore) {
      myFilesToIgnore.clear();
      myFilesMap.clear();
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
      myFilesMap.clear();
      addIgnoredFiles(filesToIgnore);
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

      final String path = FilePathsHelper.convertPath(file);
      final IgnoredFileBean fileBean = myFilesMap.get(path);
      if (fileBean != null && fileBean.matchesFile(file)) return true;

      for(IgnoredFileBean bean: myFilesToIgnore) {
        if (bean.matchesFile(file)) return true;
      }
      return false;
    }
  }
}
