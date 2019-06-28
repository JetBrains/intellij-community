// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;

final class IconPreloader implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode() && !application.isHeadlessEnvironment()) {
      application.executeOnPooledThread(() -> {
        new AsyncProcessIcon("");
        new AsyncProcessIcon.Big("");
        new AnimatedIcon.Blinking(AllIcons.Ide.FatalError);
        new AnimatedIcon.FS();
        AllIcons.Ide.Shadow.Top.getIconHeight();
      });
    }
  }
}
