/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@State(
  name = "GroovyConsoleState",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class GroovyConsoleStateService implements PersistentStateComponent<GroovyConsoleStateService.MyState> {

  public static class Entry {
    public String url;
    public String moduleName;
    public String title;
  }

  public static class MyState {
    public Collection<Entry> list = ContainerUtil.newArrayList();
  }

  private final ModuleManager myModuleManager;
  private final VirtualFileManager myFileManager;
  private final Map<VirtualFile, Pair<Module, String>> myFileModuleMap =
    Collections.synchronizedMap(ContainerUtil.<VirtualFile, Pair<Module, String>>newHashMap());

  public GroovyConsoleStateService(ModuleManager manager, VirtualFileManager fileManager) {
    myModuleManager = manager;
    myFileManager = fileManager;
  }

  @NotNull
  @Override
  public MyState getState() {
    synchronized (myFileModuleMap) {
      final MyState result = new MyState();
      for (Map.Entry<VirtualFile, Pair<Module, String>> entry : myFileModuleMap.entrySet()) {
        final VirtualFile file = entry.getKey();
        final Pair<Module, String> pair = entry.getValue();
        final Module module = pair == null ? null : pair.first;
        final Entry e = new Entry();
        e.url = file.getUrl();
        e.moduleName = module == null ? "" : module.getName();
        e.title = pair == null ? "" : pair.second;
        result.list.add(e);
      }
      return result;
    }
  }

  @Override
  public void loadState(MyState state) {
    synchronized (myFileModuleMap) {
      myFileModuleMap.clear();
      for (Entry entry : state.list) {
        final VirtualFile file = myFileManager.findFileByUrl(entry.url);
        final Module module = myModuleManager.findModuleByName(entry.moduleName);
        if (file != null) {
          myFileModuleMap.put(file, Pair.create(module, entry.title));
        }
      }
    }
  }

  public boolean isProjectConsole(@NotNull VirtualFile file) {
    return myFileModuleMap.containsKey(file);
  }

  @Nullable
  public Module getSelectedModule(@NotNull VirtualFile file) {
    final Pair<Module, String> pair = myFileModuleMap.get(file);
    return pair == null ? null : pair.first;
  }

  @Nullable
  public String getSelectedModuleTitle(@NotNull VirtualFile file) {
    final Pair<Module, String> pair = myFileModuleMap.get(file);
    return pair == null ? null : pair.second;
  }

  public void setFileModule(@NotNull VirtualFile file, @NotNull Module module) {
    myFileModuleMap.put(file, Pair.create(module, GroovyConsoleUtil.getTitle(module)));
  }

  @NotNull
  public static GroovyConsoleStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GroovyConsoleStateService.class);
  }
}
