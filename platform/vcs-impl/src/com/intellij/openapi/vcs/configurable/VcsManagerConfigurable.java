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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictConfigurable;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class VcsManagerConfigurable extends SearchableConfigurable.Parent.Abstract {
  public static final Icon ICON = IconLoader.getIcon("/general/configurableVcs.png");
  private final Project myProject;
  private VcsDirectoryConfigurationPanel myMappings;
  private VcsGeneralConfigurationPanel myGeneralPanel;

  public VcsManagerConfigurable(Project project) {
    myProject = project;
  }

  @Override
  public JComponent createComponent() {
    myMappings = new VcsDirectoryConfigurationPanel(myProject);
    if (myGeneralPanel != null) {
      addListenerToGeneralPanel();
    }
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
    myMappings = null;
  }

  public String getDisplayName() {
    return VcsBundle.message("version.control.main.configurable.name");
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Mappings";
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getId() {
    return getHelpTopic();
  }

  protected Configurable[] buildConfigurables() {
    myGeneralPanel = new VcsGeneralConfigurationPanel(myProject){
      @Override
      public void disposeUIResources() {
        super.disposeUIResources();
        myGeneralPanel = null;
      }
    };

    if (myMappings != null) {
      myGeneralPanel.updateAvailableOptions(myMappings.getActiveVcses());
      addListenerToGeneralPanel();
    }
    else {
      myGeneralPanel.updateAvailableOptions(Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()));
    }

    List<Configurable> result = new ArrayList<Configurable>();

    result.add(myGeneralPanel);
    result.add(new VcsBackgroundOperationsConfigurationPanel(myProject));
    result.add(new IgnoredSettingsPanel(myProject));
    /*if (!myProject.isDefault()) {
      result.add(new CacheSettingsPanel(myProject));
    }*/
    result.add(new IssueNavigationConfigurationPanel(myProject));
    result.add(new ChangelistConflictConfigurable(ChangeListManagerImpl.getInstanceImpl(myProject)));
    VcsDescriptor[] vcses = ProjectLevelVcsManager.getInstance(myProject).getAllVcss();

    if (vcses.length > 0) {
      result.add(createVcsComposeConfigurable(vcses));
    }

    return result.toArray(new Configurable[result.size()]);

  }

  private void addListenerToGeneralPanel() {
    myMappings.addVcsListener(new ModuleVcsListener() {
      public void activeVcsSetChanged(Collection<AbstractVcs> activeVcses) {
        myGeneralPanel.updateAvailableOptions(activeVcses);
      }
    });
  }

  private Configurable createVcsComposeConfigurable(final VcsDescriptor[] vcses) {
    return new SearchableConfigurable.Parent.Abstract(){
      protected Configurable[] buildConfigurables() {
        List<Configurable> result = new ArrayList<Configurable>();
        for (VcsDescriptor vcs : vcses) {
          result.add(createVcsConfigurableWrapper(vcs));
        }
        return result.toArray(new Configurable[result.size()]);
      }

      public String getId() {
        return "project.propVCSSupport.vcses";
      }

      @Nls
      public String getDisplayName() {
        return "VCSs";
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return "project.propVCSSupport.VCSs";
      }
    };
  }

  private Configurable createVcsConfigurableWrapper(final VcsDescriptor vcs) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final Configurable delegate = vcsManager.findVcsByName(vcs.getName()).getConfigurable();
    return new SearchableConfigurable(){
      @Nls
      public String getDisplayName() {
        return vcs.getDisplayName();
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return delegate.getHelpTopic();
      }

      public JComponent createComponent() {
        return delegate.createComponent();
      }

      public boolean isModified() {
        return delegate.isModified();
      }

      public void apply() throws ConfigurationException {
        delegate.apply();
      }

      public void reset() {
        delegate.reset();
      }

      public void disposeUIResources() {
        delegate.disposeUIResources();
      }

      public String getId() {
        return delegate instanceof SearchableConfigurable ? ((SearchableConfigurable) delegate).getId() : getHelpTopic();
      }

      public Runnable enableSearch(String option) {
        return delegate instanceof SearchableConfigurable ? ((SearchableConfigurable) delegate).enableSearch(option) : null;
      }
    };
  }

}
