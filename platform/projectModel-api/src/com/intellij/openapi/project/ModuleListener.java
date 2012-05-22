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
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;

import java.util.EventListener;
import java.util.List;

/**
 * @author max
 */
public interface ModuleListener extends EventListener {
  void moduleAdded(Project project, Module module);

  void beforeModuleRemoved(Project project, Module module);

  void moduleRemoved(Project project, Module module);

  void modulesRenamed(Project project, List<Module> modules);
}
