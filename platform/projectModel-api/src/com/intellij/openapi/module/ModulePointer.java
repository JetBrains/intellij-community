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
package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reliable and efficient reference to (probably non-existing) module by its name. If you have a part of a project configuration
 * which refers to a module by name, you can store an instance returned by {@link ModulePointerManager#create(String)} instead of storing the module name.
 * This allows you to get a Module instance via {@link #getModule()} which is more efficient than {@link ModuleManager#findModuleByName}, and
 * {@link #getModuleName() module name} encapsulated inside the instance will be properly updated if the module it refers to is renamed.
 */
public interface ModulePointer {
  @Nullable 
  Module getModule();

  @NotNull
  String getModuleName();
}
