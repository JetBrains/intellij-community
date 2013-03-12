/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JetBrainsTvAction extends AnAction implements DumbAware {
  public static final String JETBRAINS_TV_URL = "http://tv.jetbrains.net/";

  private final String myUrl;

  public JetBrainsTvAction() {
    myUrl = JETBRAINS_TV_URL;
  }

  protected JetBrainsTvAction(@NotNull @NonNls final String channel) {
    final String fullProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    getTemplatePresentation().setText(fullProductName + " TV");
    getTemplatePresentation().setDescription(UIBundle.message("welcome.screen.jetbrains.tv.action.description", fullProductName));
    myUrl = JETBRAINS_TV_URL + "channel/" + channel;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser(myUrl);
  }
}