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
package com.intellij.openapi.components;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class PathMacroManager implements PathMacroSubstitutor {
  public static PathMacroManager getInstance(@NotNull ComponentManager componentManager) {
    final PathMacroManager component = (PathMacroManager)componentManager.getPicoContainer().getComponentInstance(PathMacroManager.class);
    assert component != null;
    return component;
  }

  public abstract void collapsePathsRecursively(@NotNull Element element);

  @NotNull
  public abstract String collapsePathsRecursively(@NotNull String text);

  @NotNull
  public abstract TrackingPathMacroSubstitutor createTrackingSubstitutor();
}
