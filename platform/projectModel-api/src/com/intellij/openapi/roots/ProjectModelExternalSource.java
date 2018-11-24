/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Describes an external project system (e.g. Maven, Gradle, sbt) from which a project model element can be imported.
 *
 * @see ProjectModelElement#getExternalSource()
 * @author nik
 */
@ApiStatus.Experimental
public interface ProjectModelExternalSource {
  @NotNull
  String getDisplayName();

  @NotNull
  String getId();
}
