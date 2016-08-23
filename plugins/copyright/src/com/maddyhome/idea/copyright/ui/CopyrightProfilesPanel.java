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

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.ExternalOptionHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyrightProfilesPanel extends MasterDetailsComponent implements SearchableConfigurable {

  private final Project myProject;
  private final CopyrightManager myManager;
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  private Runnable myUpdate;

  public CopyrightProfilesPanel(Project project) {
    myProject = project;
    myManager = CopyrightManager.getInstance(project);
    initTree();
  }

  public void setUpdate(Runnable update) {
    myUpdate = update;
  }

  @Override
  protected MasterDetailsStateService getStateService() {
    return MasterDetailsStateService.getInstance(myProject);
  }

  @Override
  protected String getComponentStateKey() {
    return "Copyright.UI";
  }

  @Override
  protected void processRemovedItems() {
    Map<String, CopyrightProfile> profiles = getAllProfiles();
    final List<CopyrightProfile> deleted = new ArrayList<>();
    for (CopyrightProfile profile : myManager.getCopyrights()) {
      if (!profiles.containsValue(profile)) {
        deleted.add(profile);
      }
    }
    for (CopyrightProfile profile : deleted) {
      myManager.removeCopyright(profile);
    }
  }

  @Override
  protected boolean wasObjectStored(Object o) {
    return myManager.getCopyrights().contains((CopyrightProfile)o);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Copyright Profiles";
  }

  @Override
  @NotNull
  @NonNls
  public String getHelpTopic() {
    return "copyright.profiles";
  }

  protected void reloadAvailableProfiles() {
    if (myUpdate != null) {
      myUpdate.run();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    final Set<String> profiles = new HashSet<>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      MyNode node = (MyNode)myRoot.getChildAt(i);
      final String profileName = ((CopyrightConfigurable)node.getConfigurable()).getEditableObject().getName();
      if (profiles.contains(profileName)) {
        selectNodeInTree(profileName);
        throw new ConfigurationException("Duplicate copyright profile name: \'" + profileName + "\'");
      }
      profiles.add(profileName);
    }
    super.apply();
  }

  public Map<String, CopyrightProfile> getAllProfiles() {
    final Map<String, CopyrightProfile> profiles = new HashMap<>();
    if (!myInitialized.get()) {
      for (CopyrightProfile profile : myManager.getCopyrights()) {
        profiles.put(profile.getName(), profile);
      }
    }
    else {
      for (int i = 0; i < myRoot.getChildCount(); i++) {
        MyNode node = (MyNode)myRoot.getChildAt(i);
        final CopyrightProfile copyrightProfile = ((CopyrightConfigurable)node.getConfigurable()).getEditableObject();
        profiles.put(copyrightProfile.getName(), copyrightProfile);
      }
    }
    return profiles;
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myInitialized.set(false);
  }

  @Override
  @Nullable
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new DumbAwareAction("Add", "Add", IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
      }

      @Override
      public void actionPerformed(AnActionEvent event) {
        final String name = askForProfileName("Create Copyright Profile", "");
        if (name == null) return;
        final CopyrightProfile copyrightProfile = new CopyrightProfile(name);
        addProfileNode(copyrightProfile);
      }
    });
    result.add(new MyDeleteAction());
    result.add(new DumbAwareAction("Copy", "Copy", PlatformIcons.COPY_ICON) {
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
      }

      @Override
      public void actionPerformed(AnActionEvent event) {
        final String profileName = askForProfileName("Copy Copyright Profile", "");
        if (profileName == null) return;
        final CopyrightProfile clone = new CopyrightProfile();
        clone.copyFrom((CopyrightProfile)getSelectedObject());
        clone.setName(profileName);
        addProfileNode(clone);
      }

      @Override
      public void update(AnActionEvent event) {
        super.update(event);
        event.getPresentation().setEnabled(getSelectedObject() != null);
      }
    });
    result.add(new DumbAwareAction("Import", "Import", PlatformIcons.IMPORT_ICON) {
      @Override
      public void actionPerformed(AnActionEvent event) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
          .withFileFilter(file -> {
            final FileType fileType = file.getFileType();
            return fileType != PlainTextFileType.INSTANCE && (fileType == StdFileTypes.IDEA_MODULE || fileType == StdFileTypes.XML);
          })
          .withTitle("Choose File Containing Copyright Notice");
        FileChooser.chooseFile(descriptor, myProject, null, file -> {
          final List<CopyrightProfile> profiles = ExternalOptionHelper.loadOptions(VfsUtilCore.virtualToIoFile(file));
          if (profiles == null) return;
          if (!profiles.isEmpty()) {
            if (profiles.size() == 1) {
              importProfile(profiles.get(0));
            }
            else {
              JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<CopyrightProfile>("Choose profile to import", profiles) {
                  @Override
                  public PopupStep onChosen(final CopyrightProfile selectedValue, boolean finalChoice) {
                    return doFinalStep(() -> importProfile(selectedValue));
                  }

                  @NotNull
                  @Override
                  public String getTextFor(CopyrightProfile value) {
                    return value.getName();
                  }
                })
                .showUnderneathOf(myNorthPanel);
            }
          }
          else {
            Messages.showWarningDialog(myProject, "The selected file does not contain any copyright settings.", "Import Failure");
          }
        });
      }

      private void importProfile(CopyrightProfile copyrightProfile) {
        final String profileName = askForProfileName("Import copyright profile", copyrightProfile.getName());
        if (profileName == null) return;
        copyrightProfile.setName(profileName);
        addProfileNode(copyrightProfile);
        Messages.showInfoMessage(myProject, "The copyright settings have been successfully imported.", "Import Complete");
      }
    });
    return result;
  }


  @Nullable
  private String askForProfileName(String title, String initialName) {
    return Messages.showInputDialog("New copyright profile name:", title, Messages.getQuestionIcon(), initialName, new InputValidator() {
      @Override
      public boolean checkInput(String s) {
        return !getAllProfiles().containsKey(s) && s.length() > 0;
      }

      @Override
      public boolean canClose(String s) {
        return checkInput(s);
      }
    });
  }

  private void addProfileNode(CopyrightProfile copyrightProfile) {
    final CopyrightConfigurable copyrightConfigurable = new CopyrightConfigurable(myProject, copyrightProfile, TREE_UPDATER);
    copyrightConfigurable.setModified(true);
    final MyNode node = new MyNode(copyrightConfigurable);
    addNode(node, myRoot);
    selectNodeInTree(node);
    reloadAvailableProfiles();
  }

  @Override
  protected void removePaths(TreePath... paths) {
    super.removePaths(paths);
    reloadAvailableProfiles();
  }

  private void reloadTree() {
    myRoot.removeAllChildren();
    Collection<CopyrightProfile> collection = myManager.getCopyrights();
    for (CopyrightProfile profile : collection) {
      CopyrightProfile clone = new CopyrightProfile();
      clone.copyFrom(profile);
      addNode(new MyNode(new CopyrightConfigurable(myProject, clone, TREE_UPDATER)), myRoot);
    }
    myInitialized.set(true);
  }

  @Override
  public void reset() {
    reloadTree();
    super.reset();
  }

  @Override
  protected String getEmptySelectionString() {
    return "Select a profile to view or edit its details here";
  }

  public void addItemsChangeListener(final Runnable runnable) {
    addItemsChangeListener(new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        SwingUtilities.invokeLater(runnable);
      }

      @Override
      public void itemsExternallyChanged() {
        SwingUtilities.invokeLater(runnable);
      }
    });
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
