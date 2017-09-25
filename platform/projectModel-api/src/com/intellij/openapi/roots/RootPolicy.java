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


import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class RootPolicy<R> {
  public R visitOrderEntry(@NotNull OrderEntry orderEntry, R value) {
    return value;
  }

  public R visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry moduleSourceOrderEntry, R value) {
    return visitOrderEntry(moduleSourceOrderEntry, value);
  }

  public R visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry, R value) {
    return visitOrderEntry(libraryOrderEntry, value);
  }

  public R visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, R value) {
    return visitOrderEntry(moduleOrderEntry, value);
  }

  public R visitJdkOrderEntry(@NotNull JdkOrderEntry jdkOrderEntry, R value) {
    return visitOrderEntry(jdkOrderEntry, value);
  }

  public R visitModuleJdkOrderEntry(@NotNull ModuleJdkOrderEntry jdkOrderEntry, R value) {
    return visitJdkOrderEntry(jdkOrderEntry, value);
  }

  public R visitInheritedJdkOrderEntry(@NotNull InheritedJdkOrderEntry inheritedJdkOrderEntry, R initialValue) {
    return visitJdkOrderEntry(inheritedJdkOrderEntry, initialValue);
  }
}
