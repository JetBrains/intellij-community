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

import com.intellij.openapi.options.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.ui.*;

import javax.swing.*;
import java.util.*;

public class HgUpdateEnvironment implements UpdateEnvironment {

  private final Project project;
  private final HgUpdaterFactory hgUpdaterFactory;
  private final HgUpdater.UpdateConfiguration updateConfiguration = new HgUpdater.UpdateConfiguration();

  public HgUpdateEnvironment(Project project) {
    this.project = project;
    hgUpdaterFactory = new HgUpdaterFactory(project);
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] contentRoots,
    UpdatedFiles updatedFiles, ProgressIndicator indicator,
    @NotNull Ref<SequentialUpdatesContext> context) {
    
    List<VcsException> exceptions = new LinkedList<VcsException>();

    for (FilePath contentRoot : contentRoots) {
      if (indicator != null && indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (indicator != null) {
        indicator.startNonCancelableSection();
      }
      VirtualFile repository =
        ProjectLevelVcsManager.getInstance(project).getVcsRootFor(contentRoot);
      if (repository == null) {
        continue;
      }
      try {
        HgUpdater updater = hgUpdaterFactory.buildUpdater(repository, updateConfiguration);
        updater.update(updatedFiles, indicator, exceptions);
      } catch (VcsException e) {
        //TODO include module name where exception occurred
        exceptions.add(e);
      }
      if (indicator != null) {
        indicator.finishNonCancelableSection();
      }
    }
    return new UpdateSessionAdapter(exceptions, false);
  }

  public Configurable createConfigurable(Collection<FilePath> contentRoots) {
    return new UpdateConfigurable(updateConfiguration);
  }

  public boolean validateOptions(Collection<FilePath> roots) {
    return true;
  }
  
  public static class UpdateConfigurable implements Configurable {
    private final HgUpdater.UpdateConfiguration updateConfiguration;
    protected HgUpdateDialog updateDialog;

    public UpdateConfigurable(HgUpdater.UpdateConfiguration updateConfiguration) {
      this.updateConfiguration = updateConfiguration;
    }

    @Nls
    public String getDisplayName() {
      return "Update";
    }

    public Icon getIcon() {
      return IconLoader.getIcon("/images/mercurial.png");
    }

    public String getHelpTopic() {
      return null;
    }

    public JComponent createComponent() {
      updateDialog = new HgUpdateDialog();
      return updateDialog.createCenterPanel();
    }

    public boolean isModified() {
      return true;
    }

    public void apply() throws ConfigurationException {
      updateDialog.applyTo(updateConfiguration);
    }

    public void reset() {
      updateDialog.updateFrom(updateConfiguration);
    }

    public void disposeUIResources() {
      updateDialog = null;
    }
  }

}
