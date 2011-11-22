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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
@State(
    name = "GradleSettings",
    storages = {
      @Storage(file = "$PROJECT_FILE$"),
      @Storage(file = "$PROJECT_CONFIG_DIR$/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleSettings implements PersistentStateComponent<GradleSettings>, Cloneable {
  
  public String GRADLE_HOME;
  
  @Override
  public GradleSettings getState() {
    return this;
  }

  @Override
  public void loadState(GradleSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public static GradleSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }

  @Override
  protected GradleSettings clone() throws CloneNotSupportedException {
    GradleSettings result = new GradleSettings();
    result.GRADLE_HOME = GRADLE_HOME;
    return result;
  }

  @Override
  public String toString() {
    return "home: " + GRADLE_HOME;
  }
}