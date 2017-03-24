/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ClipboardAnalyzeListener;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

public class PatchClipboardTracker implements Disposable, ApplicationComponent {
  private static final PatchClipboardListener LISTENER = new PatchClipboardListener();

  @Override
  public void initComponent() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ApplicationActivationListener.TOPIC, LISTENER);
  }

  @Override
  public void dispose() {
  }

  private static class PatchClipboardListener extends ClipboardAnalyzeListener {
    @Override
    public void applicationActivated(IdeFrame ideFrame) {
      // we can't get clipboard details especially content size, so we should avoid clipboard processing when it's possible;
      if (!VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY) return;
      super.applicationActivated(ideFrame);
    }

    @Override
    public void applicationDeactivated(IdeFrame ideFrame) {
      if (!VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY) return;
      super.applicationDeactivated(ideFrame);
    }

    @Override
    protected void handle(@NotNull Project project, @NotNull String value) {
      new ApplyPatchFromClipboardAction.MyApplyPatchFromClipboardDialog(project, value).show();
    }

    @Override
    public boolean canHandle(@NotNull String value) {
      return PatchReader.isPatchContent(value);
    }
  }
}
