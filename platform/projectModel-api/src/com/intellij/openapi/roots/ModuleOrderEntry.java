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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ModuleOrderEntry extends ExportableOrderEntry {
  @Nullable
  Module getModule();

  @NotNull
  @NlsSafe String getModuleName();

  /**
   * If {@code true} test sources roots from the dependency will be included into production classpath for the module containing this entry.
   */
  boolean isProductionOnTestDependency();

  @ApiStatus.Internal
  void setProductionOnTestDependency(boolean productionOnTestDependency);
}
