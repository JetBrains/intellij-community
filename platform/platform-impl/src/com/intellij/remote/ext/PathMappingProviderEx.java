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
package com.intellij.remote.ext;

import com.intellij.remote.PathMappingProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Extends PathMappingsProvider with presentation info.
 * It will be used in UI to show path mappings in groups with provider description.
 */
public abstract class PathMappingProviderEx extends PathMappingProvider {

  @NotNull
  public abstract PathMappingType getMappingType();
}
