/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;

import java.util.Arrays;

public class StartUseVcsAction extends AnAction implements DumbAware {
  @Override
  public void update(final AnActionEvent e) {
    final VcsDataWrapper data = new VcsDataWrapper(e);
    final boolean enabled = data.enabled();

    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
    if (enabled) {
      presentation.setText(VcsBundle.message("action.enable.version.control.integration.text"));
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final VcsDataWrapper data = new VcsDataWrapper(e);
    final boolean enabled = data.enabled();
    if (! enabled) {
      return;
    }

    final StartUseVcsDialog dialog = new StartUseVcsDialog(data);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final String vcsName = dialog.getVcs();
      if (vcsName.length() > 0) {
        final ProjectLevelVcsManager manager = data.getManager();
        manager.findVcsByName(vcsName).generalPreConfigurationStep();
        manager.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping("", vcsName)));
      }
    }
  }

}
