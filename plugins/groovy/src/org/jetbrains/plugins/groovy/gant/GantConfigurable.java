// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

public class GantConfigurable extends SdkHomeConfigurable implements Configurable.NoScroll {

  public GantConfigurable(Project project) {
    super(project, GroovyBundle.message("configurable.GantConfigurable.display.name"));
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "reference.settingsdialog.project.gant";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    return GantUtils.isGantSdkHome(file);
  }

  @Override
  protected GantSettings getFrameworkSettings() {
    return GantSettings.getInstance(myProject);
  }

}
