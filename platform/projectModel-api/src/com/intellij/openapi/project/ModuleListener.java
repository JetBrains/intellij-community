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
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * @author max
 */
public interface ModuleListener extends EventListener {
  void moduleAdded(@NotNull Project project, @NotNull Module module);

  void beforeModuleRemoved(@NotNull Project project, @NotNull Module module);

  void moduleRemoved(@NotNull Project project, @NotNull Module module);

  void modulesRenamed(@NotNull Project project, @NotNull List<Module> modules, @NotNull Function<Module, String> oldNameProvider);
}
