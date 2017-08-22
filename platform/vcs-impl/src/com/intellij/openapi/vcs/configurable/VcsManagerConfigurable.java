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
package com.intellij.openapi.vcs.configurable;

import com.intellij.application.options.colors.fileStatus.FileStatusColorsConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictConfigurable;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.options.ex.ConfigurableWrapper.wrapConfigurable;
import static com.intellij.util.ArrayUtil.toObjectArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;

public class VcsManagerConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  @NotNull private final Project myProject;
  private VcsDirectoryConfigurationPanel myMappings;
  private VcsGeneralConfigurationConfigurable myGeneralPanel;

  public VcsManagerConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public JComponent createComponent() {
    myMappings = new VcsDirectoryConfigurationPanel(myProject);
    return myMappings;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public boolean isModified() {
    return myMappings != null && myMappings.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    myMappings.apply();
  }

  @Override
  public void reset() {
    super.reset();
    myMappings.reset();
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    if (myMappings != null) {
      myMappings.disposeUIResources();
    }
    if (myGeneralPanel != null) {
      myGeneralPanel.disposeUIResources();
    }
    myMappings = null;
  }

  @Override
  public String getDisplayName() {
    return VcsBundle.message("version.control.main.configurable.name");
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "project.propVCSSupport.Mappings";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  protected Configurable[] buildConfigurables() {
    myGeneralPanel = new VcsGeneralConfigurationConfigurable(myProject, this);

    List<Configurable> result = newArrayList();

    result.add(myGeneralPanel);
    result.add(new VcsBackgroundOperationsConfigurable(myProject));
    if (!myProject.isDefault()) {
      result.add(new IgnoredSettingsPanel(myProject));
    }
    result.add(new IssueNavigationConfigurationPanel(myProject));
    if (!myProject.isDefault()) {
      result.add(new ChangelistConflictConfigurable(ChangeListManagerImpl.getInstanceImpl(myProject)));
    }
    result.add(new CommitDialogConfigurable(myProject));
    result.add(new ShelfProjectConfigurable(myProject));  
    for (VcsConfigurableProvider provider : VcsConfigurableProvider.EP_NAME.getExtensions()) {
      addIfNotNull(result, provider.getConfigurable(myProject));
    }

    result.add(new FileStatusColorsConfigurable());

    Set<String> projectConfigurableIds = map2Set(myProject.getExtensions(Configurable.PROJECT_CONFIGURABLE), ep -> ep.id);
    for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(myProject).getAllVcss()) {
      if (!projectConfigurableIds.contains(getVcsConfigurableId(descriptor.getDisplayName()))) {
        result.add(wrapConfigurable(new VcsConfigurableEP(myProject, descriptor)));
      }
    }

    return toObjectArray(result, Configurable.class);
  }

  @Nullable
  public VcsDirectoryConfigurationPanel getMappings() {
    return myMappings;
  }

  @NotNull
  public static String getVcsConfigurableId(@NotNull String displayName) {
    return "vcs." + displayName;
  }

  private static class VcsConfigurableEP extends ConfigurableEP<Configurable> {

    private static final int WEIGHT = -500;

    @NotNull private final VcsDescriptor myDescriptor;

    public VcsConfigurableEP(@NotNull Project project, @NotNull VcsDescriptor descriptor) {
      super(project);
      myDescriptor = descriptor;
      displayName = descriptor.getDisplayName();
      id = getVcsConfigurableId(descriptor.getDisplayName());
      groupWeight = WEIGHT;
    }

    @NotNull
    @Override
    protected ConfigurableEP.ObjectProducer createProducer() {
      return new ObjectProducer() {
        @Override
        protected Object createElement() {
          return notNull(ProjectLevelVcsManager.getInstance(getProject()).findVcsByName(myDescriptor.getName())).getConfigurable();
        }

        @Override
        protected boolean canCreateElement() {
          return true;
        }

        @Override
        protected Class<?> getType() {
          return SearchableConfigurable.class;
        }
      };
    }
  }
}
