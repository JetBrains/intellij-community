// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.keymap.KeymapUtil;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.StatusText;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.ExternalOptionHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class CopyrightProfilesPanel extends MasterDetailsComponent implements SearchableConfigurable {
  private final Project myProject;
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  private Runnable myUpdate;

  CopyrightProfilesPanel(Project project) {
    myProject = project;
    initTree();
  }

  @Override
  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, treePath -> {
      MasterDetailsComponent.MyNode obj = (MyNode)treePath.getLastPathComponent();
      return obj == null ? null : obj.getDisplayName();
    }, true);

    StatusText emptyText = myTree.getEmptyText();
    emptyText.setText(CopyrightBundle.message("copyright.profiles.empty"));
    emptyText.appendSecondaryText(CopyrightBundle.message("copyright.profiles.add.profile"), SimpleTextAttributes.LINK_ATTRIBUTES, __ -> doAddProfile());
    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD));
    if (!shortcutText.isEmpty()) {
      emptyText.appendSecondaryText(" (" + shortcutText + ")", StatusText.DEFAULT_ATTRIBUTES, null);
    }
  }

  void setUpdate(Runnable update) {
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
    CopyrightManager manager = CopyrightManager.getInstance(myProject);
    for (CopyrightProfile profile : new ArrayList<>(manager.getCopyrights())) {
      if (!profiles.containsValue(profile)) {
        manager.removeCopyright(profile);
      }
    }
  }

  @Override
  protected boolean wasObjectStored(Object o) {
    return CopyrightManager.getInstance(myProject).getCopyrights().contains((CopyrightProfile)o);
  }

  @Override
  public String getDisplayName() {
    return CopyrightBundle.message("configurable.CopyrightProfilesPanel.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getHelpTopic() {
    return "copyright.profiles";
  }

  private void reloadAvailableProfiles() {
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
        throw new ConfigurationException(CopyrightBundle.message("dialog.message.duplicate.copyright.profile.name", profileName));
      }
      profiles.add(profileName);
    }
    super.apply();
  }

  Map<String, CopyrightProfile> getAllProfiles() {
    final Map<String, CopyrightProfile> profiles = new HashMap<>();
    if (!myInitialized.get()) {
      for (CopyrightProfile profile : CopyrightManager.getInstance(myProject).getCopyrights()) {
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

  private void doAddProfile() {
    String name = askForProfileName(CopyrightBundle.message("create.copyright.profile"), "");
    if (name != null) {
      addProfileNode(new CopyrightProfile(name));
    }
  }

  @Override
  protected @NotNull ArrayList<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new DumbAwareAction(CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.text.add"),
                                   CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.description.add"),
                                   IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), myTree);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        doAddProfile();
      }
    });
    result.add(new MyDeleteAction());
    result.add(new DumbAwareAction(
      CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.text.copy"),
      CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.description.copy"),
      PlatformIcons.COPY_ICON) {
      {
        registerCustomShortcutSet(CommonShortcuts.getDuplicate(), myTree);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        String profileName = askForProfileName(CopyrightBundle.message("copy.copyright.profile"), "");
        if (profileName == null) {
          return;
        }

        CopyrightProfile clone = new CopyrightProfile();
        clone.copyFrom((CopyrightProfile)Objects.requireNonNull(getSelectedObject()));
        clone.setName(profileName);
        addProfileNode(clone);
      }

      @Override
      public void update(@NotNull AnActionEvent event) {
        super.update(event);
        event.getPresentation().setEnabled(getSelectedObject() != null);
      }
    });
    result.add(new DumbAwareAction(CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.text.import"),
                                   CopyrightBundle.messagePointer("action.DumbAware.CopyrightProfilesPanel.description.import"),
                                   PlatformIcons.IMPORT_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
          .withFileFilter(file -> {
            final FileType fileType = file.getFileType();
            return fileType == ModuleFileType.INSTANCE || fileType == XmlFileType.INSTANCE;
          })
          .withTitle(CopyrightBundle.message("dialog.file.chooser.title.choose.file.containing.copyright.notice"));
        FileChooser.chooseFile(descriptor, myProject, null, file -> {
          final List<CopyrightProfile> profiles = ExternalOptionHelper.loadOptions(VfsUtilCore.virtualToIoFile(file));
          if (profiles == null) return;
          if (!profiles.isEmpty()) {
            if (profiles.size() == 1) {
              importProfile(profiles.get(0));
            }
            else {
              JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<>(CopyrightBundle.message("popup.title.choose.profile.to.import"), profiles) {
                  @Override
                  public PopupStep<?> onChosen(final CopyrightProfile selectedValue, boolean finalChoice) {
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
            Messages.showWarningDialog(myProject,
                                       CopyrightBundle.message("dialog.message.the.selected.file.copyright.settings"),
                                       CopyrightBundle.message("dialog.title.import.failure"));
          }
        });
      }

      private void importProfile(CopyrightProfile copyrightProfile) {
        final String profileName = askForProfileName(CopyrightBundle.message("import.copyright.profile"), copyrightProfile.getName());
        if (profileName == null) return;
        copyrightProfile.setName(profileName);
        addProfileNode(copyrightProfile);
        Messages.showInfoMessage(myProject,
                                 CopyrightBundle.message("dialog.message.the.copyright.settings.imported"),
                                 CopyrightBundle.message("dialog.title.import.complete"));
      }
    });
    return result;
  }


  @Nullable
  private String askForProfileName(@NlsContexts.DialogTitle String title, String initialName) {
    return Messages.showInputDialog(CopyrightBundle.message("dialog.message.new.copyright.profile.name"), title, Messages.getQuestionIcon(), initialName, new InputValidator() {
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

  private void addProfileNode(@NotNull CopyrightProfile copyrightProfile) {
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
    Collection<CopyrightProfile> collection = CopyrightManager.getInstance(myProject).getCopyrights();
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
    return CopyrightBundle.message("copyright.profiles.select.profile");
  }

  void addItemsChangeListener(final Runnable runnable) {
    addItemsChangeListener(new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        ApplicationManager.getApplication().invokeLater(runnable);
      }

      @Override
      public void itemsExternallyChanged() {
        ApplicationManager.getApplication().invokeLater(runnable);
      }
    });
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
