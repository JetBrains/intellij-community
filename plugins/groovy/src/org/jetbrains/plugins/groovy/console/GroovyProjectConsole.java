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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

@State(
  name = "GroovyProjectConsole",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/groovyConsole.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GroovyProjectConsole implements PersistentStateComponent<GroovyProjectConsole.MyState> {

  public static class MyState {
    public Map<String, String> map = ContainerUtil.newHashMap();
  }

  private final ModuleManager myModuleManager;
  private final VirtualFileManager myFileManager;
  private final Map<VirtualFile, Module> myFileModuleMap = Collections.synchronizedMap(ContainerUtil.<VirtualFile, Module>newHashMap());


  public GroovyProjectConsole(ModuleManager manager, VirtualFileManager fileManager) {
    myModuleManager = manager;
    myFileManager = fileManager;
  }

  @NotNull
  @Override
  public MyState getState() {
    synchronized (myFileModuleMap) {
      final MyState result = new MyState();
      for (VirtualFile file : myFileModuleMap.keySet()) {
        final Module module = myFileModuleMap.get(file);
        result.map.put(
          file.getCanonicalPath(),
          module == null ? "" : module.getName()
        );
      }
      return result;
    }
  }

  @Override
  public void loadState(MyState state) {
    synchronized (myFileModuleMap) {
      myFileModuleMap.clear();
      for (Map.Entry<String, String> entry : state.map.entrySet()) {
        final VirtualFile file = myFileManager.findFileByUrl(entry.getKey());
        final Module module = myModuleManager.findModuleByName(entry.getValue());
        if (file != null) {
          myFileModuleMap.put(file, module);
        }
      }
    }
  }

  public boolean isProjectConsole(@NotNull VirtualFile file) {
    return myFileModuleMap.keySet().contains(file);
  }

  @Nullable
  public Module getSelectedModule(@NotNull VirtualFile file) {
    return myFileModuleMap.get(file);
  }

  public void setFileModule(@NotNull VirtualFile file, @NotNull Module module) {
    myFileModuleMap.put(file, module);
  }

  @NotNull
  public static GroovyProjectConsole getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GroovyProjectConsole.class);
  }
}
