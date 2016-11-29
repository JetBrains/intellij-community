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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ClipboardAnalyzeListener;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;


public class PatchClipboardTracker extends ApplicationComponent.Adapter {
  private static final PatchClipboardListener LISTENER = new PatchClipboardListener();
  private MessageBusConnection myConnection;

  @Override
  public void initComponent() {
    myConnection = ApplicationManagerEx.getApplicationEx().getMessageBus().connect();
    myConnection.subscribe(ApplicationActivationListener.TOPIC, LISTENER);
  }

  @Override
  public void disposeComponent() {
    myConnection.disconnect();
    myConnection = null;
  }

  private static class PatchClipboardListener extends ClipboardAnalyzeListener {
    @Override
    protected void handle(@NotNull Project project, @NotNull String value) {
      new ApplyPatchFromClipboardAction.MyApplyPatchFromClipboardDialog(project, value).show();
    }

    @Override
    public boolean canHandle(@NotNull String value) {
      if (!VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY) return false;
      return PatchReader.isPatchContent(value);
    }
  }
}
