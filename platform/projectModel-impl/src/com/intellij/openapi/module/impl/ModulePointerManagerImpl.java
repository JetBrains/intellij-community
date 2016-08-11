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
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ModulePointerManagerImpl extends ModulePointerManager {
  private final Map<String, ModulePointerImpl> myUnresolved = new HashMap<>();
  private final Map<Module, ModulePointerImpl> myPointers = new HashMap<>();
  private final Project myProject;

  public ModulePointerManagerImpl(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
        unregisterPointer(module);
      }

      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        moduleAppears(module);
      }

      @Override
      public void modulesRenamed(@NotNull Project project, @NotNull List<Module> modules, @NotNull Function<Module, String> oldNameProvider) {
        for (Module module : modules) {
          moduleAppears(module);
        }
      }
    });
  }

  private void moduleAppears(Module module) {
    ModulePointerImpl pointer = myUnresolved.remove(module.getName());
    if (pointer != null && pointer.getModule() == null) {
      pointer.moduleAdded(module);
      registerPointer(module, pointer);
    }
  }

  private void registerPointer(final Module module, final ModulePointerImpl pointer) {
    myPointers.put(module, pointer);
    Disposer.register(module, new Disposable() {
      @Override
      public void dispose() {
        unregisterPointer(module);
      }
    });
  }

  private void unregisterPointer(Module module) {
    final ModulePointerImpl pointer = myPointers.remove(module);
    if (pointer != null) {
      pointer.moduleRemoved(module);
      myUnresolved.put(pointer.getModuleName(), pointer);
    }
  }

  @NotNull
  @Override
  public ModulePointer create(@NotNull Module module) {
    ModulePointerImpl pointer = myPointers.get(module);
    if (pointer == null) {
      pointer = myUnresolved.get(module.getName());
      if (pointer == null) {
        pointer = new ModulePointerImpl(module);
      }
      else {
        pointer.moduleAdded(module);
      }
      registerPointer(module, pointer);
    }
    return pointer;
  }

  @NotNull
  @Override
  public ModulePointer create(@NotNull String moduleName) {
    final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleName);
    if (module != null) {
      return create(module);
    }

    ModulePointerImpl pointer = myUnresolved.get(moduleName);
    if (pointer == null) {
      pointer = new ModulePointerImpl(moduleName);
      myUnresolved.put(moduleName, pointer);
    }
    return pointer;
  }
}
