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
package com.intellij.xdebugger.impl.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable implements SearchableConfigurable.Parent, OptionalConfigurable {
  private Configurable myRootConfigurable;
  private Configurable[] myChildren;
  private WeakReference<Project> myContextProject;

  public static final String DISPLAY_NAME = XDebuggerBundle.message("debugger.configurable.display.name");

  public DebuggerConfigurable(ProjectManager pm) {
    pm.addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectClosed(final Project project) {
        myChildren = null;
        myRootConfigurable = null;
      }
    });
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return myRootConfigurable != null? myRootConfigurable.getHelpTopic() : null;
  }

  public Configurable[] getConfigurables() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    if (myContextProject == null || myContextProject.get() != project) {
      // clear cached data built for the previous projects 
      myChildren = null;
      myRootConfigurable = null;
      myContextProject = new WeakReference<Project>(project);
    }

    if (myChildren == null) {
      final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
      final List<DebuggerSettingsPanelProvider> providers = new ArrayList<DebuggerSettingsPanelProvider>();
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        providers.add(support.getSettingsPanelProvider());
      }
      Collections.sort(providers, new Comparator<DebuggerSettingsPanelProvider>() {
        public int compare(final DebuggerSettingsPanelProvider o1, final DebuggerSettingsPanelProvider o2) {
          return o2.getPriority() - o1.getPriority();
        }
      });
      for (DebuggerSettingsPanelProvider provider : providers) {
        configurables.addAll(provider.getConfigurables(project));
        final Configurable rootConfigurable = provider.getRootConfigurable();
        if (rootConfigurable != null) {
          if (myRootConfigurable != null) {
            configurables.add(rootConfigurable);
          }
          else {
            myRootConfigurable = rootConfigurable;
          }
        }
      }
      myChildren = configurables.toArray(new Configurable[configurables.size()]);
    }
    return myChildren;
  }

  public void apply() throws ConfigurationException {
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  public boolean hasOwnContent() {
    return myRootConfigurable != null;
  }

  public boolean isVisible() {
    return true;
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public JComponent createComponent() {
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  public void disposeUIResources() {
    if (myRootConfigurable != null) {
      myRootConfigurable.disposeUIResources();
    }
  }

  @NonNls
  public String getId() {
    return "project.propDebugger";
  }

  public boolean needDisplay() {
    DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : supports) {
      if (support.getSettingsPanelProvider().hasAnySettingsPanels()) {
        return true;
      }
    }
    return false;
  }
}