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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictConfigurable;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class VcsManagerConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  private final Project myProject;
  private VcsDirectoryConfigurationPanel myMappings;
  private VcsGeneralConfigurationConfigurable myGeneralPanel;

  public VcsManagerConfigurable(Project project) {
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
  public boolean isVisible() {
    return ProjectLevelVcsManager.getInstance(myProject).getAllVcss().length > 0;
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
  public String getHelpTopic() {
    return "project.propVCSSupport.Mappings";
  }

  @Override
  @NotNull
  public String getId() {
    return getDefaultConfigurableIdValue(this);
  }

  @NotNull
  private static String getDefaultConfigurableIdValue(final Configurable configurable) {
    final String helpTopic = configurable.getHelpTopic();
    return helpTopic == null ? configurable.getClass().getName() : helpTopic;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    myGeneralPanel = new VcsGeneralConfigurationConfigurable(myProject, this);

    List<Configurable> result = new ArrayList<>();

    result.add(myGeneralPanel);
    result.add(new VcsBackgroundOperationsConfigurable(myProject));

    if (!myProject.isDefault()) {
      result.add(new IgnoredSettingsPanel(myProject));
    }
    /*if (!myProject.isDefault()) {
      result.add(new CacheSettingsPanel(myProject));
    }*/
    result.add(new IssueNavigationConfigurationPanel(myProject));
    if (!myProject.isDefault()) {
      result.add(new ChangelistConflictConfigurable(ChangeListManagerImpl.getInstanceImpl(myProject)));
    }

    for (VcsConfigurableProvider provider : VcsConfigurableProvider.EP_NAME.getExtensions()) {
      final Configurable configurable = provider.getConfigurable(myProject);
      if (configurable != null) {
        result.add(configurable);
      }
    }

    VcsDescriptor[] vcses = ProjectLevelVcsManager.getInstance(myProject).getAllVcss();
    for (VcsDescriptor vcs : vcses) {
      result.add(createVcsConfigurableWrapper(vcs));
    }

    return result.toArray(new Configurable[result.size()]);
  }

  public VcsDirectoryConfigurationPanel getMappings() {
    return myMappings;
  }

  private Configurable createVcsConfigurableWrapper(final VcsDescriptor vcs) {
    final NotNullLazyValue<Configurable> delegate = new NotNullLazyValue<Configurable>() {
      @NotNull
      @Override
      protected Configurable compute() {
        return ProjectLevelVcsManager.getInstance(myProject).findVcsByName(vcs.getName()).getConfigurable();
      }
    };
    return new SearchableConfigurable(){

      @Override
      @Nls
      public String getDisplayName() {
        return vcs.getDisplayName();
      }

      @Override
      public String getHelpTopic() {
        return delegate.getValue().getHelpTopic();
      }

      @Override
      public JComponent createComponent() {
        return delegate.getValue().createComponent();
      }

      @Override
      public boolean isModified() {
        return delegate.getValue().isModified();
      }

      @Override
      public void apply() throws ConfigurationException {
        delegate.getValue().apply();
      }

      @Override
      public void reset() {
        delegate.getValue().reset();
      }

      @Override
      public void disposeUIResources() {
        delegate.getValue().disposeUIResources();
      }

      @Override
      @NotNull
      public String getId() {
        return "vcs." + getDisplayName();
      }

      @Override
      public String toString() {
        return "VcsConfigurable for "+vcs.getDisplayName();
      }
    };
  }
}
