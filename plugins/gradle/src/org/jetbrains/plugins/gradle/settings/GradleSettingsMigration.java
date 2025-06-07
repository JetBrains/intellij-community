// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.plugins.gradle.settings.TestRunner.PLATFORM;

@Service(Service.Level.PROJECT)
@State(name = "GradleMigrationSettings", storages = @Storage("gradle.xml"))
public final class GradleSettingsMigration implements PersistentStateComponent<Element> {
  private Element myElement = new Element("settings");

  public int getMigrationVersion() {
    return StringUtil.parseInt(myElement.getAttributeValue("migrationVersion"), 0);
  }

  public void setMigrationVersion(int version) {
    myElement.setAttribute("migrationVersion", String.valueOf(version));
  }

  @Override
  public @NotNull Element getState() {
    return myElement;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myElement = state;
  }

  @State(name = "DefaultGradleProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
  public static class LegacyDefaultGradleProjectSettings implements PersistentStateComponent<LegacyDefaultGradleProjectSettings.MyState> {
    private @Nullable MyState myState = null;

    @Override
    public @Nullable MyState getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull MyState state) {
      myState = state;
    }

    public static class MyState {
      public @NotNull TestRunner testRunner = PLATFORM;
      public boolean delegatedBuild = false;
    }
  }
}