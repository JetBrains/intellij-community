// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.jetbrains.annotations.NotNull;

class ToggleBreadcrumbsSettingsAction extends ToggleBreadcrumbsAction {

  static final class ShowAbove extends ToggleBreadcrumbsSettingsAction {
    ShowAbove() {
      super(true, true);
    }
  }

  static final class ShowBelow extends ToggleBreadcrumbsSettingsAction {
    ShowBelow() {
      super(true, false);
    }
  }

  static final class HideBoth extends ToggleBreadcrumbsSettingsAction {
    HideBoth() {
      super(false, false);
    }
  }

  private final boolean show;
  private final boolean above;

  private ToggleBreadcrumbsSettingsAction(boolean show, boolean above) {
    this.show = show;
    this.above = above;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    boolean selected = super.isSelected(event);
    if (show && selected) {
      return above == EditorSettingsExternalizable.getInstance().isBreadcrumbsAbove();
    }
    return !show && !selected;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean selected) {
    Editor editor = findEditor(event);
    boolean modified = editor != null && BreadcrumbsForceShownSettings.setForcedShown(null, editor);
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings.setBreadcrumbsShown(show)) modified = true;
    if (show) {
      if (settings.setBreadcrumbsAbove(above)) modified = true;
      String languageID = findLanguageID(event);
      if (languageID != null && settings.setBreadcrumbsShownFor(languageID, true)) modified = true;
    }
    if (modified) {
      UISettings.getInstance().fireUISettingsChanged();
    }
  }
}
