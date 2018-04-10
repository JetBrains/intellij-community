// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
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
public class JavaFxSettings implements PersistentStateComponent<JavaFxSettings> {
  public String myPathToSceneBuilder = null;
  
  public static JavaFxSettings getInstance() {
    return ServiceManager.getService(JavaFxSettings.class);
  }

  public JavaFxSettings getState() {
    return this;
  }

  public void loadState(@NotNull JavaFxSettings object) {
    XmlSerializerUtil.copyBean(object, this);
  }

  @Nullable
  public String getPathToSceneBuilder() {
    return myPathToSceneBuilder;
  }

  public void setPathToSceneBuilder(String pathToSceneBuilder) {
    myPathToSceneBuilder = pathToSceneBuilder;
  }
}