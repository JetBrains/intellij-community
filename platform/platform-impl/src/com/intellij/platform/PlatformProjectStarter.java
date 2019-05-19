// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class PlatformProjectStarter {
  PlatformProjectStarter() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appFrameCreated(@NotNull List<String> commandLineArgs, @NotNull Ref<? super Boolean> willOpenProject) {
        if (!commandLineArgs.isEmpty()) {
          willOpenProject.set(Boolean.TRUE);
        }
      }
    });
  }
}