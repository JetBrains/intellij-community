/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrameProvider;
import org.jetbrains.annotations.NotNull;

public class CheckForUpdateAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    String place = e.getPlace();
    if (ActionPlaces.WELCOME_SCREEN.equals(place) && FlatWelcomeFrameProvider.isAvailable()) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu || !ActionPlaces.MAIN_MENU.equals(place));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UpdateChecker.updateAndShowResult(e.getProject(), null);
  }
}
