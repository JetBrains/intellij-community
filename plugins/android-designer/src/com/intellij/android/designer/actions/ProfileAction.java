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
package com.intellij.android.designer.actions;

import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.profile.Profile;
import com.intellij.android.designer.profile.ProfileDialog;
import com.intellij.android.designer.profile.ProfileList;
import com.intellij.android.designer.profile.ProfileManager;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ProfileAction implements Disposable {
  private static final Profile EDIT_PROFILE = new Profile();

  static {
    EDIT_PROFILE.setName("Edit Profiles");
  }

  private final AndroidDesignerEditorPanel myDesigner;
  private final ProfileManager myProfileManager;
  private final AbstractComboBoxAction<Profile> myProfileAction;
  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private final ProfileList myProfileList;
  private int myVersion;

  public ProfileAction(AndroidDesignerEditorPanel designer, Runnable refreshAction) {
    myDesigner = designer;

    myProfileList = ProfileList.getInstance(myDesigner.getProject());

    myProfileManager = new ProfileManager(myDesigner, refreshAction, new Runnable() {
      @Override
      public void run() {
        myProfileList.addVersion();
        myVersion = myProfileList.getVersion();
      }
    });
    Disposer.register(this, myProfileManager);

    myProfileAction = new AbstractComboBoxAction<Profile>() {
      @Override
      protected boolean addSeparator(DefaultActionGroup actionGroup, Profile item) {
        if (item == EDIT_PROFILE) {
          actionGroup.addSeparator();
        }
        return false;
      }

      @Override
      protected void update(Profile item, Presentation presentation, boolean popup) {
        presentation.setText(item.getName());
      }

      @Override
      protected boolean selectionChanged(Profile item) {
        if (item == EDIT_PROFILE) {
          editProfiles();
        }
        else {
          myProfileList.addVersion();
          myVersion = myProfileList.getVersion();
          updateActions(item);
        }
        return item != EDIT_PROFILE;
      }
    };

    DefaultActionGroup designerActionGroup = myDesigner.getActionPanel().getActionGroup();
    designerActionGroup.add(myProfileAction);
    designerActionGroup.add(myActionGroup);

    updateActions();
  }

  public ProfileManager getProfileManager() {
    return myProfileManager;
  }

  private void updateActions() {
    myVersion = myProfileList.getVersion();

    List<Profile> profiles = new ArrayList<Profile>(myProfileList.getProfiles());
    profiles.add(myProfileList.getFullProfile());
    profiles.add(EDIT_PROFILE);

    Profile profile = myProfileList.getProfile();
    myProfileAction.setItems(profiles, profile);
    updateActions(profile);
  }

  private void updateActions(Profile profile) {
    myProfileManager.setProfile(profile);
    myProfileList.setSelection(profile.getName());
    myActionGroup.removeAll();

    if (profile.isShowDevice()) {
      myActionGroup.add(myProfileManager.getDeviceAction());
    }
    if (profile.isShowDeviceConfiguration()) {
      myActionGroup.add(myProfileManager.getDeviceConfigurationAction());
    }
    if (profile.isShowTarget()) {
      myActionGroup.add(myProfileManager.getTargetAction());
    }
    if (profile.isShowLocale()) {
      myActionGroup.add(myProfileManager.getLocaleAction());
    }
    if (profile.isShowDockMode()) {
      myActionGroup.add(myProfileManager.getDockModeAction());
    }
    if (profile.isShowNightMode()) {
      myActionGroup.add(myProfileManager.getNightModeAction());
    }
    if (profile.isShowTheme()) {
      myActionGroup.add(myProfileManager.getThemeAction());
    }

    myDesigner.getActionPanel().update();
  }

  private void editProfiles() {
    ProfileDialog dialog = new ProfileDialog(myDesigner, myProfileList.getProfiles());
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      myProfileList.setProfiles(dialog.getResult());
      myProfileList.addVersion();
      updateActions();
    }
  }

  public void externalUpdate() {
    Sdk sdk = myProfileList.getModuleSdk(myDesigner.getModule());
    if ((sdk != null && !sdk.equals(getCurrentSdk()))) {
      myProfileManager.update(sdk);
    }
    else if (myVersion != myProfileList.getVersion()) {
      updateActions();
    }
  }

  @Nullable
  public Sdk getCurrentSdk() {
    return myProfileManager.getSdk();
  }

  public int getVersion() {
    return myVersion;
  }

  @Override
  public void dispose() {
  }
}