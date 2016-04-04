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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementFinder;
import org.jetbrains.plugins.groovy.util.SdkHomeBean;
import org.jetbrains.plugins.groovy.util.SdkHomeSettings;

/**
 * @author peter
 */
@State(name = "GantSettings", storages = @Storage("gant_config.xml"))
public class GantSettings extends SdkHomeSettings {
  private final Project myProject;

  public GantSettings(Project project) {
    super(project);
    myProject = project;
  }

  public static GantSettings getInstance(Project project) {
    return ServiceManager.getService(project, GantSettings.class);
  }

  @Override
  public void loadState(SdkHomeBean state) {
    SdkHomeBean oldState = getState();
    super.loadState(state);
    if (oldState != null) {
      Extensions.findExtension(PsiElementFinder.EP_NAME, myProject, GantClassFinder.class).clearCache();
    }
  }
}
