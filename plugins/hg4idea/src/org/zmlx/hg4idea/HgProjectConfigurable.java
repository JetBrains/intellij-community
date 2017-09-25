// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.ui.HgConfigurationProjectPanel;

import javax.swing.*;

public class HgProjectConfigurable implements SearchableConfigurable {

  public static final String DISPLAY_NAME = HgVcsMessages.message("hg4idea.mercurial");

  private final HgConfigurationProjectPanel myPanel;
  @NotNull private final Project myProject;

  public HgProjectConfigurable(@NotNull Project project, HgProjectSettings projectSettings) {
    myProject = project;
    myPanel = new HgConfigurationProjectPanel(projectSettings, myProject);
  }

  @Nls
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.VCSs.Mercurial";
  }

  public JComponent createComponent() {
    return myPanel.getPanel();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() {
    myPanel.saveSettings();
    if (myPanel.getProjectSettings().isCheckIncomingOutgoing()) {
      BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).show();
    }
    else {
      BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).hide();
    }
  }

  public void reset() {
    myPanel.loadSettings();
  }

  public void disposeUIResources() {
  }

  @NotNull
  public String getId() {
    return "vcs.Mercurial";
  }
}
