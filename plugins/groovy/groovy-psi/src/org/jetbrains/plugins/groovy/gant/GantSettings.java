// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.SdkHomeBean;
import org.jetbrains.plugins.groovy.util.SdkHomeSettings;

@Service(Service.Level.PROJECT)
@State(name = "GantSettings", storages = @Storage("ant.xml"))
public final class GantSettings extends SdkHomeSettings {
  private final Project myProject;

  public GantSettings(@NotNull Project project) {
    super(project);

    myProject = project;
  }

  public static GantSettings getInstance(@NotNull Project project) {
    return project.getService(GantSettings.class);
  }

  @Override
  public void loadState(@NotNull SdkHomeBean state) {
    SdkHomeBean oldState = getState();
    super.loadState(state);
    if (oldState != null) {
      PsiElementFinder.EP.findExtension(GantClassFinder.class, myProject).clearCache();
    }
  }
}
