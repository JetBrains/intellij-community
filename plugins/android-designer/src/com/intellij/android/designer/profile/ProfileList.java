/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.profile;

import com.android.sdklib.devices.DeviceManager;
import com.intellij.ProjectTopics;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 * @author Eugene.Kudelevsky
 */
@State(name = "AndroidDesignerProfile", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class ProfileList implements PersistentStateComponent<ProfileList.ProfileState> {
  private final DeviceManager myLayoutDeviceManager;
  private final Map<Module, Sdk> myModule2Sdk = new HashMap<Module, Sdk>();
  private final Project myProject;
  private ProfileState myState = new ProfileState();
  private int myVersion;

  public ProfileList(Project project) {
    myProject = project;
    myLayoutDeviceManager = new DeviceManager(new MessageBuildingSdkLog());
    updateModules();
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        updatePlatform();
      }
    });
  }

  public static ProfileList getInstance(Project project) {
    return ServiceManager.getService(project, ProfileList.class);
  }

  public synchronized Sdk getModuleSdk(Module module) {
    return myModule2Sdk.get(module);
  }

  private void updateModules() {
    myModule2Sdk.clear();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      myModule2Sdk.put(module, ModuleRootManager.getInstance(module).getSdk());
    }
  }

  private synchronized void updatePlatform() {
    updateModules();

    DesignerEditorPanel designer = DesignerToolWindowManager.getInstance(myProject).getActiveDesigner();
    if (designer instanceof AndroidDesignerEditorPanel) {
      ((AndroidDesignerEditorPanel)designer).getProfileAction().externalUpdate();
    }
  }

  public List<Profile> getProfiles() {
    return myState.getProfiles();
  }

  public void setProfiles(List<Profile> profiles) {
    myState.setProfiles(profiles);
  }

  public void setSelection(String selection) {
    myState.setSelection(selection);
  }

  public Profile getFullProfile() {
    return myState.getFullProfile();
  }

  public DeviceManager getLayoutDeviceManager() {
    return myLayoutDeviceManager;
  }

  public Profile getProfile() {
    String selection = myState.getSelection();
    for (Profile profile : getProfiles()) {
      if (selection.equals(profile.getName())) {
        return profile;
      }
    }
    return getFullProfile();
  }

  public int getVersion() {
    return myVersion;
  }

  public void addVersion() {
    myVersion++;
  }

  @Override
  public ProfileState getState() {
    return myState;
  }

  @Override
  public void loadState(ProfileState state) {
    myState = state;
  }

  public static class ProfileState {
    private List<Profile> myProfiles = new ArrayList<Profile>();
    private Profile myFullProfile = new Profile();
    private String mySelection = "";

    @Tag("profiles")
    @AbstractCollection(surroundWithTag = false)
    public List<Profile> getProfiles() {
      return myProfiles;
    }

    public void setProfiles(List<Profile> profiles) {
      myProfiles = profiles;
    }

    public String getSelection() {
      return mySelection;
    }

    public void setSelection(String selection) {
      mySelection = selection;
    }

    public Profile getFullProfile() {
      return myFullProfile;
    }

    public void setFullProfile(Profile fullProfile) {
      myFullProfile = fullProfile;
    }
  }
}