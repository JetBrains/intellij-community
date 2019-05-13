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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class VcsDataWrapper {
  private final Project myProject;
  private final ProjectLevelVcsManager myManager;
  private Map<String, String> myVcses;

  VcsDataWrapper(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    if (myProject == null || myProject.isDefault()) {
      myManager = null;
      myVcses = null;
      return;
    }
    myManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  public boolean enabled() {
    if (myProject == null || myProject.isDefault() || myManager == null) {
      return false;
    }
    if (checkMappings()) {
      return false;
    }
    if (! ((ProjectLevelVcsManagerImpl) myManager).haveVcses()) {
      return false;
    }
    return true;
  }

  private boolean checkMappings() {
    final List<VcsDirectoryMapping> mappings = myManager.getDirectoryMappings();
    for (VcsDirectoryMapping mapping : mappings) {
      final String vcs = mapping.getVcs();
      if (vcs != null && vcs.length() > 0) {
        return true;
      }
    }
    return false;
  }

  public Project getProject() {
    return myProject;
  }

  public ProjectLevelVcsManager getManager() {
    return myManager;
  }

  public Map<String, String> getVcses() {
    if (myVcses == null && myProject != null && !myProject.isDefault()) {
      final VcsDescriptor[] allVcss = myManager.getAllVcss();
      myVcses = new HashMap<>(allVcss.length, 1);
      for (VcsDescriptor vcs : allVcss) {
        myVcses.put(vcs.getDisplayName(), vcs.getName());
      }
    }
    return myVcses;
  }
}
