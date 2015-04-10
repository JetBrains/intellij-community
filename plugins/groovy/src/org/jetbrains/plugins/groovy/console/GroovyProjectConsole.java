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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@State(
  name = "GroovyProjectConsole",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/other.xml", scheme = StorageScheme.DIRECTORY_BASED),
    @Storage(file = StoragePathMacros.PROJECT_FILE)
  }
)
public class GroovyProjectConsole implements PersistentStateComponent<GroovyProjectConsole.State> {

  public static class State {
    @AbstractCollection(surroundWithTag = false, elementTag = "path")
    public Collection<String> paths = ContainerUtil.newArrayList();

    public State() {
    }

    public State(Collection<String> paths) {
      this.paths = paths;
    }
  }

  private final Set<VirtualFile> files = Collections.synchronizedSet(ContainerUtil.<VirtualFile>newHashSet());

  @NotNull
  @Override
  public State getState() {
    synchronized (files) {
      return new State(
        ContainerUtil.filter(ContainerUtil.map(files, new Function<VirtualFile, String>() {
          @Override
          public String fun(VirtualFile file) {
            return file.getCanonicalPath();
          }
        }), Condition.NOT_NULL)
      );
    }
  }

  @Override
  public void loadState(State state) {
    final VirtualFileManager fileManager = VirtualFileManager.getInstance();
    synchronized (files) {
      files.clear();
      ContainerUtil.addAllNotNull(files, ContainerUtil.map(state.paths, new Function<String, VirtualFile>() {
        @Override
        public VirtualFile fun(String path) {
          return fileManager.findFileByUrl(path);
        }
      }));
    }
  }

  public boolean isProjectConsole(@NotNull VirtualFile file) {
    return files.contains(file);
  }

  public void addProjectConsole(@NotNull VirtualFile file) {
    files.add(file);
  }

  @NotNull
  public static GroovyProjectConsole getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GroovyProjectConsole.class);
  }
}
