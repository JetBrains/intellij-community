// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "PropertiesFoldingSettings", storages = @Storage("editor.xml"))
public class PropertiesFoldingSettings implements PersistentStateComponent<PropertiesFoldingSettings> {
  private boolean myFoldPlaceholdersToContext;

  public static PropertiesFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getService(PropertiesFoldingSettings.class);
  }

  public void setFoldPlaceholdersToContext(boolean fold) {
    myFoldPlaceholdersToContext = fold;
  }

  public boolean isFoldPlaceholdersToContext() {
    return myFoldPlaceholdersToContext;
  }

  @Override
  public PropertiesFoldingSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final PropertiesFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}