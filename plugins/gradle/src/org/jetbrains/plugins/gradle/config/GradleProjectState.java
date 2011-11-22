/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;

import java.util.Set;

/**
 * Holds settings specific for IntelliJ project mapped to the Gradle project.
 *
 * @author Denis Zhdanov
 * @since 11/17/11 5:15 PM
 */
@State(
  name = "GradleSettings",
  storages = {
    @Storage(file = "$PROJECT_FILE$"),
    @Storage(file = "$PROJECT_CONFIG_DIR$/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GradleProjectState {
  
  public final Set<GradleProjectStructureChange> CHANGES = new HashSet<GradleProjectStructureChange>();
  public String GRADLE_PROJECT_FILE_PATH;
}
