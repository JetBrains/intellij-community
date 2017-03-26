/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BooleanPropertyToggleAction extends ToggleAction implements DumbAware {
  public BooleanPropertyToggleAction() {
  }

  public BooleanPropertyToggleAction(@Nullable String text) {
    super(text);
  }

  public BooleanPropertyToggleAction(@Nullable String text,
                                     @Nullable String description,
                                     @Nullable Icon icon) {
    super(text, description, icon);
  }

  protected abstract VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty();

  @Override
  public boolean isSelected(AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties == null || !properties.exists(getProperty())) return false;
    return properties.get(getProperty());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties != null && properties.exists(getProperty())) {
      properties.set(getProperty(), state);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabledAndVisible(properties != null && properties.exists(getProperty()));

    super.update(e);
  }
}