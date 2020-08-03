// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class GreclipseIdeaCompilerSettings implements PersistentStateComponent<GreclipseSettings> {
  private final GreclipseSettings mySettings = new GreclipseSettings();

  @Override
  public GreclipseSettings getState() {
    return mySettings;
  }

  @Override
  public void loadState(@NotNull GreclipseSettings state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  @NotNull
  public static GreclipseSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, GreclipseIdeaCompilerSettings.class).mySettings;
  }

  public static void setGrEclipsePath(@NotNull Project project, @NotNull String path){
    ServiceManager.getService(project, GreclipseIdeaCompilerSettings.class).mySettings.greclipsePath = path;
  }

  public static void setGrCmdParams(@NotNull Project project, @NotNull String cmdLineParams){
    ServiceManager.getService(project, GreclipseIdeaCompilerSettings.class).mySettings.cmdLineParams = cmdLineParams;
  }
}
