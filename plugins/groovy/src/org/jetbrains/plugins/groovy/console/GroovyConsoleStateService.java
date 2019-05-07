// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    public @Nullable String url;
    public @Nullable String moduleName;
    public @Nullable String title;
  }

  public static class MyState {
    public Collection<Entry> list = ContainerUtil.newArrayList();
  }

  private final ModuleManager myModuleManager;
  private final VirtualFileManager myFileManager;
  private final Map<VirtualFile, Pair<Module, String>> myFileModuleMap = Collections.synchronizedMap(ContainerUtil.newHashMap());

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
        final Module module = Pair.getFirst(pair);
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
  public void loadState(@NotNull MyState state) {
    synchronized (myFileModuleMap) {
      myFileModuleMap.clear();
      for (Entry entry : state.list) {
        if (entry.url == null) continue;
        final VirtualFile file = myFileManager.findFileByUrl(entry.url);
        if (file == null) continue;
        String moduleName = entry.moduleName;
        final Module module = moduleName == null ? null : myModuleManager.findModuleByName(moduleName);
        myFileModuleMap.put(file, Pair.create(module, entry.title));
      }
    }
  }

  public boolean isProjectConsole(@NotNull VirtualFile file) {
    return myFileModuleMap.containsKey(file);
  }

  @Nullable
  public Module getSelectedModule(@NotNull VirtualFile file) {
    final Pair<Module, String> pair = myFileModuleMap.get(file);
    return Pair.getFirst(pair);
  }

  @Nullable
  public String getSelectedModuleTitle(@NotNull VirtualFile file) {
    final Pair<Module, String> pair = myFileModuleMap.get(file);
    return Pair.getSecond(pair);
  }

  public void setFileModule(@NotNull VirtualFile file, @NotNull Module module) {
    myFileModuleMap.put(file, Pair.create(module, GroovyConsoleUtil.getTitle(module)));
  }

  @NotNull
  public static GroovyConsoleStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GroovyConsoleStateService.class);
  }
}
