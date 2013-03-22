package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * Analogue of ToggleAction for option popups
 *
 * @author Konstantin Bulenkov
 * @since 12.1
 */
public class CheckedActionGroup extends DefaultActionGroup {
  public CheckedActionGroup() {
    super();
    setPopup(true);
  }

  public CheckedActionGroup(@NotNull AnAction... actions) {
    super(actions);
    setPopup(true);
  }
}
