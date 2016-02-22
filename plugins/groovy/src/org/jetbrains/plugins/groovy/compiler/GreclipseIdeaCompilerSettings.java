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
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.GreclipseSettings;

@State(name = GreclipseSettings.COMPONENT_NAME, storages = @Storage(GreclipseSettings.COMPONENT_FILE))
public class GreclipseIdeaCompilerSettings implements PersistentStateComponent<GreclipseSettings> {
  private final GreclipseSettings mySettings = new GreclipseSettings();

  @Override
  public GreclipseSettings getState() {
    return mySettings;
  }

  @Override
  public void loadState(GreclipseSettings state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  @NotNull
  public static GreclipseSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, GreclipseIdeaCompilerSettings.class).mySettings;
  }
}
