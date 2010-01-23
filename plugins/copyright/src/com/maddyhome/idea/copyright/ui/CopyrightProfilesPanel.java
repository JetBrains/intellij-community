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

package com.maddyhome.idea.copyright.ui;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import com.intellij.util.containers.HashMap;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.ExternalOptionHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyrightProfilesPanel extends MasterDetailsComponent {
    private static final Icon COPY_ICON = IconLoader.getIcon("/actions/copy.png");

    private final Project myProject;
    private final CopyrightManager myManager;
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  public CopyrightProfilesPanel(Project project) {
      MasterDetailsStateService.getInstance(project).register("Copyright.UI", this);
        myProject = project;
        myManager = CopyrightManager.getInstance(project);
        initTree();
    }

    protected void processRemovedItems() {
        Map<String, CopyrightProfile> profiles = getAllProfiles();
        final List<CopyrightProfile> deleted = new ArrayList<CopyrightProfile>();
        for (CopyrightProfile profile : myManager.getCopyrights()) {
            if (!profiles.containsValue(profile)) {
                deleted.add(profile);
            }
        }
        for (CopyrightProfile profile : deleted) {
            myManager.removeCopyright(profile);
        }
    }

    protected boolean wasObjectStored(Object o) {
        return myManager.getCopyrights().contains((CopyrightProfile) o);
    }

    @Nls
    public String getDisplayName() {
        return "Copyright Profiles";
    }

    @Nullable
    public Icon getIcon() {
        return null;
    }

    @Nullable
    @NonNls
    public String getHelpTopic() {
        return "copyright.profiles";
    }

    public void apply() throws ConfigurationException {
        final Set<String> profiles = new HashSet<String>();
        for (int i = 0; i < myRoot.getChildCount(); i++) {
            MyNode node = (MyNode) myRoot.getChildAt(i);
            final String profileName = ((CopyrightConfigurable) node.getConfigurable()).getEditableObject().getName();
            if (profiles.contains(profileName)) {
                selectNodeInTree(profileName);
                throw new ConfigurationException("Duplicate copyright profile name: \'" + profileName + "\'");
            }
            profiles.add(profileName);
        }
        super.apply();
    }

    public Map<String, CopyrightProfile> getAllProfiles() {
      final Map<String, CopyrightProfile> profiles = new HashMap<String, CopyrightProfile>();
        if (!myInitialized.get()) {
          for (CopyrightProfile profile : myManager.getCopyrights()) {
            profiles.put(profile.getName(), profile);
          }
        } else {
          for (int i = 0; i < myRoot.getChildCount(); i++) {
            MyNode node = (MyNode) myRoot.getChildAt(i);
            final CopyrightProfile copyrightProfile = ((CopyrightConfigurable) node.getConfigurable()).getEditableObject();
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

  @Nullable
    protected ArrayList<AnAction> createActions(boolean fromPopup) {
        ArrayList<AnAction> result = new ArrayList<AnAction>();
        result.add(new AnAction("Add", "Add", Icons.ADD_ICON) {
            {
                registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
            }
            public void actionPerformed(AnActionEvent event) {
                final String name = askForProfileName("Create new copyright profile", "");
                if (name == null) return;
                final CopyrightProfile copyrightProfile = new CopyrightProfile(name);
                addProfileNode(copyrightProfile);
            }


        });
        result.add(new MyDeleteAction(Conditions.alwaysTrue()));
        result.add(new AnAction("Copy", "Copy", COPY_ICON) {
            {
                registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_MASK)), myTree);
            }
            public void actionPerformed(AnActionEvent event) {
                final String profileName = askForProfileName("Copy copyright profile", "");
                if (profileName == null) return;
                final CopyrightProfile clone = new CopyrightProfile();
                clone.copyFrom((CopyrightProfile) getSelectedObject());
                clone.setName(profileName);
                addProfileNode(clone);
            }

            public void update(AnActionEvent event) {
                super.update(event);
                event.getPresentation().setEnabled(getSelectedObject() != null);
            }
        });
        result.add(new AnAction("Import", "Import", Icons.ADVICE_ICON) {
          public void actionPerformed(AnActionEvent event) {
            final OpenProjectFileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(true) {
              @Override
              public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                return super.isFileVisible(file, showHiddenFiles) || canContainCopyright(file);
              }

              @Override
              public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) || canContainCopyright(file);
              }

              private boolean canContainCopyright(VirtualFile file) {
                return !file.isDirectory() && (file.getFileType() == StdFileTypes.IDEA_MODULE || file.getFileType() == StdFileTypes.XML);
              }
            };
            descriptor.setTitle("Choose file containing copyright notice");
            final VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
            if (files.length != 1) return;

            final List<CopyrightProfile> copyrightProfiles = ExternalOptionHelper.loadOptions(VfsUtil.virtualToIoFile(files[0]));
            if (copyrightProfiles != null) {
              if (copyrightProfiles.size() == 1) {
                importProfile(copyrightProfiles.get(0));
              } else {
                JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<CopyrightProfile>("Choose profile to import", copyrightProfiles) {
                  @Override
                  public PopupStep onChosen(final CopyrightProfile selectedValue, boolean finalChoice) {
                    return doFinalStep(new Runnable(){
                      public void run() {
                        importProfile(selectedValue);
                      }
                    });
                  }

                  @NotNull
                  @Override
                  public String getTextFor(CopyrightProfile value) {
                    return value.getName();
                  }
                }).showUnderneathOf(myNorthPanel);
              }
            }
            else {
              Messages.showWarningDialog(myProject, "The selected file did not contain any copyright settings.", "Import Failure");
            }
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
            public boolean checkInput(String s) {
                return !getAllProfiles().containsKey(s) && s.length() > 0;
            }

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
      public void itemChanged(@Nullable Object deletedItem) {
        SwingUtilities.invokeLater(runnable);
      }

      public void itemsExternallyChanged() {
        SwingUtilities.invokeLater(runnable);
      }
    });
  }
}
