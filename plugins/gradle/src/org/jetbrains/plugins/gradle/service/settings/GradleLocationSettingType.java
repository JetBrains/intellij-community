// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;

/**
 * Enumerates possible types of external project location setting.
 */
public enum GradleLocationSettingType {

  /** User hasn't defined location but the IDE discovered it automatically. */
  DEDUCED("setting.type.location.deduced", "TextField.inactiveForeground"),

  /** User hasn't defined location and the IDE was unable to discover it automatically. */
  UNKNOWN("setting.type.location.unknown"),

  /** User defined location but it's incorrect. */
  EXPLICIT_INCORRECT("setting.type.location.explicit.incorrect"),

  EXPLICIT_CORRECT("setting.type.location.explicit.correct");

  private final @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) @NotNull String myDescriptionKey;
  private final @NotNull Color myColor;

  GradleLocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) String descriptionKey) {
    this(descriptionKey, "TextField.foreground");
  }

  GradleLocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) String descriptionKey,
                            @NotNull String key)
  {
    myDescriptionKey = descriptionKey;
    myColor = JBColor.namedColor(key, UIManager.getColor(key));
  }

  /**
   * @return human-readable description of the current setting type
   */
  public @Nls String getDescription(@NotNull ProjectSystemId externalSystemId) {
    return ExternalSystemBundle.message(myDescriptionKey, externalSystemId.getReadableName());
  }

  public @NotNull Color getColor() {
    return myColor;
  }
}
