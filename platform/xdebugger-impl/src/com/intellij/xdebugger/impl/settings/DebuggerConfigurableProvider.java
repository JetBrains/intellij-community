/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.xdebugger.impl.DebuggerSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class DebuggerConfigurableProvider extends ConfigurableProvider {
  @Override
  public Configurable createConfigurable() {
    final List<DebuggerSettingsPanelProvider> providers = new ArrayList<DebuggerSettingsPanelProvider>();
    final DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : supports) {
      providers.add(support.getSettingsPanelProvider());
    }

    final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    Collections.sort(providers, new Comparator<DebuggerSettingsPanelProvider>() {
      public int compare(final DebuggerSettingsPanelProvider o1, final DebuggerSettingsPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });

    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    Configurable rootConfigurable = null;
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurables(project));
      final Configurable aRootConfigurable = provider.getRootConfigurable();
      if (aRootConfigurable != null) {
        if (rootConfigurable != null) {
          configurables.add(aRootConfigurable);
        }
        else {
          rootConfigurable = aRootConfigurable;
        }
      }
    }
    if (configurables.isEmpty() && rootConfigurable == null) {
      return null;
    }

    return new DebuggerConfigurable(rootConfigurable, configurables);
  }
}
