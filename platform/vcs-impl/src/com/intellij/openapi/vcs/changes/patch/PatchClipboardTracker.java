// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ClipboardAnalyzeListener;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

public class PatchClipboardTracker implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, new PatchClipboardListener());
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
