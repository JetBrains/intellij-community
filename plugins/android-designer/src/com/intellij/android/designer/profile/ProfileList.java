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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
@State(name = "AndroidDesignerProfile", storages = {@Storage(file = "$WORKSPACE_FILE$")})
public class ProfileList implements PersistentStateComponent<ProfileList> {
  private List<Profile> myProfiles = new ArrayList<Profile>();
  private String mySelection = "";

  public static ProfileList getInstance(Project project) {
    return ServiceManager.getService(project, ProfileList.class);
  }

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

  @Transient
  public Profile getProfile() {
    for (Profile profile : myProfiles) {
      if (mySelection.equals(profile.getName())) {
        return profile;
      }
    }

    Profile profile = new Profile();
    mySelection = profile.getName();
    myProfiles.add(profile);

    return profile;
  }

  @Transient
  public void setProfile(Profile profile) {
    mySelection = profile.getName();
  }

  @Override
  public ProfileList getState() {
    return this;
  }

  @Override
  public void loadState(ProfileList state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}