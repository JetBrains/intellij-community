package com.intellij.ide.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(
    name = "PropertiesComponent",
    storages = {@Storage(
        id = "other",
        file = "$APP_CONFIG$/options.xml")})
public class AppPropertiesComponentImpl extends PropertiesComponentImpl implements ApplicationComponent {
  public void disposeComponent() {
  }

  public void initComponent() {
  }
}
