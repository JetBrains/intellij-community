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
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.ui.HgUpdateDialog;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HgUpdateEnvironment implements UpdateEnvironment {

  private final Project project;

  public HgUpdateEnvironment(Project project) {
    this.project = project;
  }

  @Override
  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  @Override
  @NotNull
  public UpdateSession updateDirectories(FilePath @NotNull [] contentRoots,
                                         UpdatedFiles updatedFiles, ProgressIndicator indicator,
                                         @NotNull Ref<SequentialUpdatesContext> context) {

    List<VcsException> exceptions = new LinkedList<>();

    boolean result = true;
    for (FilePath contentRoot : contentRoots) {
      if (indicator != null) {
        indicator.checkCanceled();
      }
      VirtualFile repository =
        ProjectLevelVcsManager.getInstance(project).getVcsRootFor(contentRoot);
      if (repository == null) {
        continue;
      }
      try {
        result &= ProgressManager.getInstance().computeInNonCancelableSection(() -> {
          HgUpdateConfigurationSettings updateConfiguration = project.getService(HgUpdateConfigurationSettings.class);
          HgUpdater updater = new HgRegularUpdater(project, repository, updateConfiguration);
          return updater.update(updatedFiles, indicator, exceptions);
        });
      }
      catch (VcsException e) {
        //TODO include module name where exception occurred
        exceptions.add(e);
      }
    }
    return new UpdateSessionAdapter(exceptions, !result);
  }

  @Override
  public Configurable createConfigurable(Collection<FilePath> contentRoots) {
    HgUpdateConfigurationSettings updateConfiguration = project.getService(HgUpdateConfigurationSettings.class);
    return new UpdateConfigurable(updateConfiguration);
  }

  @Override
  public boolean validateOptions(Collection<FilePath> roots) {
    return true;
  }

  public static class UpdateConfigurable implements Configurable {
    private final HgUpdateConfigurationSettings updateConfiguration;
    protected HgUpdateDialog updateDialog;

    public UpdateConfigurable(@NotNull HgUpdateConfigurationSettings updateConfiguration) {
      this.updateConfiguration = updateConfiguration;
    }

    @Nls
    @Override
  public String getDisplayName() {
    return HgBundle.message("configurable.UpdateConfigurable.display.name");
  }

    @Override
    public String getHelpTopic() {
      return "reference.VersionControl.Mercurial.UpdateProject";
    }

    @Override
    public JComponent createComponent() {
      updateDialog = new HgUpdateDialog();
      return updateDialog.getContentPanel();
    }

    @Override
    public boolean isModified() {
      return true;
    }

    @Override
    public void apply() {
      updateDialog.applyTo(updateConfiguration);
    }

    @Override
    public void reset() {
      updateDialog.updateFrom(updateConfiguration);
    }

    @Override
    public void disposeUIResources() {
      updateDialog = null;
    }
  }

}
