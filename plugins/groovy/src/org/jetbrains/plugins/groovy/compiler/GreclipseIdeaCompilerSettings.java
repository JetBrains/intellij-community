// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.GreclipseSettings;

@SuppressWarnings("LightServiceMigrationCode")
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

  public static @NotNull GreclipseSettings getSettings(@NotNull Project project) {
    return project.getService(GreclipseIdeaCompilerSettings.class).mySettings;
  }

  public static void setGrEclipsePath(@NotNull Project project, @NotNull String path){
    project.getService(GreclipseIdeaCompilerSettings.class).mySettings.greclipsePath = FileUtil.toSystemIndependentName(path);
  }

  public static void setGrCmdParams(@NotNull Project project, @NotNull String cmdLineParams){
    project.getService(GreclipseIdeaCompilerSettings.class).mySettings.cmdLineParams = cmdLineParams;
  }
}
