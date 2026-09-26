// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name="JavaFxSettings",
  storages = {
    @Storage("other.xml")}
)
public final class JavaFxSettings implements PersistentStateComponent<JavaFxSettings> {
  public String myPathToSceneBuilder = null;

  public static JavaFxSettings getInstance() {
    return ApplicationManager.getApplication().getService(JavaFxSettings.class);
  }

  @Override
  public JavaFxSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaFxSettings object) {
    XmlSerializerUtil.copyBean(object, this);
  }

  public @Nullable String getPathToSceneBuilder() {
    return myPathToSceneBuilder;
  }

  public void setPathToSceneBuilder(String pathToSceneBuilder) {
    myPathToSceneBuilder = pathToSceneBuilder;
  }
}