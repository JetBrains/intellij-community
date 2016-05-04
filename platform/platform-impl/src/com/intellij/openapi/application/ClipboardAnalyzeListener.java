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
package com.intellij.openapi.application;

import com.intellij.Patches;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClipboardAnalyzeListener extends ApplicationActivationListener.Adapter {
  private static final int MAX_SIZE = 100 * 1024;
  @Nullable private String myCachedClipboardValue;

  @Override
  public void applicationActivated(final IdeFrame ideFrame) {
    final Runnable processClipboard = () -> {
      final String clipboard = ClipboardUtil.getTextInClipboard();
      if (clipboard != null && clipboard.length() < MAX_SIZE && !clipboard.equals(myCachedClipboardValue)) {
        myCachedClipboardValue = clipboard;
        final Project project = ideFrame.getProject();
        if (project != null && !project.isDefault() && canHandle(myCachedClipboardValue)) {
          handle(project, myCachedClipboardValue);
        }
      }
    };

    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      //IDEA's clipboard is synchronized with the system clipboard on frame activation so we need to postpone clipboard processing
      new Alarm().addRequest(processClipboard, 300);
    }
    else {
      processClipboard.run();
    }
  }

  protected abstract void handle(@NotNull Project project, @NotNull String value);

  @Override
  public void applicationDeactivated(IdeFrame ideFrame) {
    if (SystemInfo.isMac) return;
    myCachedClipboardValue = ClipboardUtil.getTextInClipboard();
  }

  public abstract boolean canHandle(@NotNull String value);
}
