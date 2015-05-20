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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IgnoredFilesComponent {
  private final Set<IgnoredFileBean> myFilesToIgnore;
  private final Map<String, IgnoredFileBean> myFilesMap;
  private final Set<String> myDirectoriesManuallyRemovedFromIgnored;

  public IgnoredFilesComponent(final Project project, final boolean registerListener) {
    myFilesToIgnore = new LinkedHashSet<IgnoredFileBean>();
    myFilesMap = new HashMap<String, IgnoredFileBean>();

    if (registerListener) {
      project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {
          if (hasSignificantChanges(events)) {
            resetCaches();
          }
        }

        private <T extends VFileEvent> boolean hasSignificantChanges(List<T> events) {
          return ContainerUtil.exists(events, new Condition<T>() {
            @Override
            public boolean value(VFileEvent event) {
              return !(event instanceof VFileContentChangeEvent);
            }
          });
        }
      });
    }
    myDirectoriesManuallyRemovedFromIgnored = new HashSet<String>();
  }

  public IgnoredFilesComponent(final IgnoredFilesComponent other) {
    myFilesToIgnore = new LinkedHashSet<IgnoredFileBean>(other.myFilesToIgnore);
    myFilesMap = new HashMap<String, IgnoredFileBean>(other.myFilesMap);
    myDirectoriesManuallyRemovedFromIgnored = new HashSet<String>(other.myDirectoriesManuallyRemovedFromIgnored);
  }

  public void add(final IgnoredFileBean... filesToIgnore) {
    synchronized (myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
      addIgnoredFiles(filesToIgnore);
    }
  }

  public Set<String> getDirectoriesManuallyRemovedFromIgnored() {
    return Collections.unmodifiableSet(myDirectoriesManuallyRemovedFromIgnored);
  }

  public void setDirectoriesManuallyRemovedFromIgnored(Set<String> directories) {
    myDirectoriesManuallyRemovedFromIgnored.clear();
    myDirectoriesManuallyRemovedFromIgnored.addAll(directories);
  }

  public void addIgnoredDirectoryImplicitly(@NotNull String path, @NotNull Project project) {
    synchronized (myFilesToIgnore) {
      if (myDirectoriesManuallyRemovedFromIgnored.contains(path) || myDirectoriesManuallyRemovedFromIgnored.contains(path + "/")) {
        return;
      }
      for (IgnoredFileBean bean : myFilesToIgnore) {
        if (bean.getType() == IgnoreSettingsType.UNDER_DIR && FileUtil.isAncestor(bean.getPath(), path, false)) {
          return;
        }
      }
      List<IgnoredFileBean> toRemove = new ArrayList<IgnoredFileBean>();
      for (IgnoredFileBean bean : myFilesToIgnore) {
        if ((bean.getType() == IgnoreSettingsType.UNDER_DIR || bean.getType() == IgnoreSettingsType.FILE) &&
            FileUtil.isAncestor(path, bean.getPath(), false)) {
          toRemove.add(bean);
        }
      }
      myFilesToIgnore.removeAll(toRemove);
      myFilesToIgnore.add(IgnoredBeanFactory.ignoreUnderDirectory(path, project));
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
    synchronized (myFilesToIgnore) {
      myFilesToIgnore.clear();
      Collections.addAll(myFilesToIgnore, filesToIgnore);
      myFilesMap.clear();
      addIgnoredFiles(filesToIgnore);
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    synchronized (myFilesToIgnore) {
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
    synchronized (myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) return false;

      final String path = FilePathsHelper.convertPath(file);
      final IgnoredFileBean fileBean = myFilesMap.get(path);
      if (fileBean != null && fileBean.matchesFile(file)) return true;

      for (IgnoredFileBean bean : myFilesToIgnore) {
        if (bean.matchesFile(file)) return true;
      }
      return false;
    }
  }
}
