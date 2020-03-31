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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.components.PersistentStateComponent;

/**
 * Represents additional properties of a library. Use {@link com.intellij.openapi.roots.libraries.DummyLibraryProperties} if libraries of
 * a custom type don't have any additional properties.
 */
public abstract class LibraryProperties<T> implements PersistentStateComponent<T> {
  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
