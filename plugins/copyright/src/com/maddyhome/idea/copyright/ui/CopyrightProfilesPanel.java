// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.AbstractCopyrightManager;
import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.copyright.IdeCopyrightManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.ui.LayeredIcon;
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
  private final CopyrightManager myCopyrightManager;
  private final IdeCopyrightManager myIdeCopyrightManager;

  private Runnable myUpdate;

  CopyrightProfilesPanel(Project project) {
    myProject = project;
    initTree();
    myCopyrightManager = CopyrightManager.getInstance(myProject);
    myIdeCopyrightManager = IdeCopyrightManager.getInstance();
  }

  @Override
  protected void initTree() {
    super.initTree();
    TreeSpeedSearch.installOn(myTree, true, treePath -> {
      MasterDetailsComponent.MyNode obj = (MyNode)treePath.getLastPathComponent();
      return obj == null ? null : obj.getDisplayName();
    });

    StatusText emptyText = myTree.getEmptyText();
    emptyText.setText(CopyrightBundle.message("copyright.profiles.empty"));
    emptyText.appendSecondaryText(CopyrightBundle.message("copyright.profiles.add.profile"), SimpleTextAttributes.LINK_ATTRIBUTES, __ -> doAddProfile(true));
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
    processRemovedItemps(myCopyrightManager, profiles);
    processRemovedItemps(myIdeCopyrightManager, profiles);
  }

  private static void processRemovedItemps(AbstractCopyrightManager manager, Map<String, CopyrightProfile> profiles) {
    for (CopyrightProfile profile : new ArrayList<>(manager.getCopyrights())) {
      if (!profiles.containsValue(profile)) {
        manager.removeCopyright(profile);
      }
    }
  }

  @Override
  protected boolean wasObjectStored(Object o) {
    CopyrightProfile profile = (CopyrightProfile)o;
    return myCopyrightManager.getCopyrights().contains(profile) || myIdeCopyrightManager.getCopyrights().contains(profile);
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
      for (CopyrightProfile profile : myCopyrightManager.getCopyrights()) {
        profiles.put(profile.getName(), profile);
      }

      for (CopyrightProfile profile : myIdeCopyrightManager.getCopyrights()) {
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

  private void doAddProfile(boolean shareProfile) {
    String name = askForProfileName(CopyrightBundle.message("create.copyright.profile"), "");
    if (name != null) {
      addProfileNode(new CopyrightProfile(name), shareProfile);
    }
  }

  @Override
  protected @NotNull ArrayList<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new MyAddActionGroup(fromPopup));
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
        @Nullable CopyrightProfile profile = null;
        boolean sharedProfile = true;
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null && selectionPath.getLastPathComponent() instanceof MyNode node) {
          final NamedConfigurable<?> configurable = node.getConfigurable();
          LOG.assertTrue(configurable != null, "already disposed");
          profile = (CopyrightProfile)configurable.getEditableObject();
          sharedProfile = ((CopyrightConfigurable)configurable).isShareProfile();
        }
        CopyrightProfile copyrightProfile = Objects.requireNonNull(profile);
        clone.copyFrom(copyrightProfile);
        clone.setName(profileName);
        addProfileNode(clone, sharedProfile);
      }

      @Override
      public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(getSelectedObject() != null);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
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
        addProfileNode(copyrightProfile, true);
        Messages.showInfoMessage(myProject,
                                 CopyrightBundle.message("dialog.message.the.copyright.settings.imported"),
                                 CopyrightBundle.message("dialog.title.import.complete"));
      }
    });
    return result;
  }

  private final class MyAddActionGroup extends ActionGroup implements ActionGroupWithPreselection, DumbAware {

    private AnAction[] myChildren;
    private final boolean myFromPopup;

    MyAddActionGroup(boolean fromPopup) {
      super(CopyrightBundle.messagePointer("action.add.profile.text"), !fromPopup);
      myFromPopup = fromPopup;
      getTemplatePresentation().setIcon(LayeredIcon.ADD_WITH_DROPDOWN);
      registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), myTree);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (myChildren == null) {
        myChildren = new AnAction[2];
        myChildren[0] = new DumbAwareAction(CopyrightBundle.messagePointer("action.local.text"),
                                            CopyrightBundle.messagePointer("action.stored.in.ide.description"), () -> null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            doAddProfile(false);
          }
        };
        myChildren[1] = new DumbAwareAction(CopyrightBundle.messagePointer("action.shared.text"),
                                            CopyrightBundle.messagePointer("action.stored.in.project.description"), () -> null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            doAddProfile(true);
          }
        };
      }
      if (myFromPopup) {
        final AnAction action = myChildren[getDefaultIndex()];
        action.getTemplatePresentation().setIcon(IconUtil.getAddIcon());
        return new AnAction[]{action};
      }
      return myChildren;
    }

    @Override
    public ActionGroup getActionGroup() {
      return this;
    }

    @Override
    public int getDefaultIndex() {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null &&
          ((MyNode)selectionPath.getLastPathComponent()).getConfigurable() instanceof CopyrightConfigurable c) {
        return c.isShareProfile() ? 1 : 0;
      }
      return 1;
    }
  }

  @Nullable
  private String askForProfileName(@NlsContexts.DialogTitle String title, String initialName) {
    return Messages.showInputDialog(CopyrightBundle.message("dialog.message.new.copyright.profile.name"), title, Messages.getQuestionIcon(), initialName, new InputValidator() {
      @Override
      public boolean checkInput(String s) {
        return !getAllProfiles().containsKey(s) && !s.isEmpty();
      }

      @Override
      public boolean canClose(String s) {
        return checkInput(s);
      }
    });
  }

  private void addProfileNode(@NotNull CopyrightProfile copyrightProfile, boolean shareProfile) {
    final CopyrightConfigurable copyrightConfigurable = new CopyrightConfigurable(myProject, copyrightProfile, TREE_UPDATER, shareProfile);
    copyrightConfigurable.setModified(true);
    final MyNode node = createCopyrightNode(copyrightConfigurable);
    addNode(node, myRoot);
    selectNodeInTree(node);
    reloadAvailableProfiles();
  }

  @NotNull
  private static MyNode createCopyrightNode(CopyrightConfigurable copyrightConfigurable) {
    return new MyNode(copyrightConfigurable) {
      @Override
      public String getLocationString() {
        return ((CopyrightConfigurable)getConfigurable()).isShareProfile()
               ? CopyrightBundle.message("copyright.shared.description")
               : CopyrightBundle.message("copyright.local.description");
      }
    };
  }

  @Override
  protected void removePaths(TreePath... paths) {
    super.removePaths(paths);
    reloadAvailableProfiles();
  }

  private void reloadTree() {
    myRoot.removeAllChildren();
    createTreeNodes(myCopyrightManager.getCopyrights(), true);
    createTreeNodes(myIdeCopyrightManager.getCopyrights(), false);
    myInitialized.set(true);
  }

  private void createTreeNodes(Collection<CopyrightProfile> collection, boolean shareProfile) {
    for (CopyrightProfile profile : collection) {
      CopyrightProfile clone = new CopyrightProfile();
      clone.copyFrom(profile);
      addNode(createCopyrightNode(new CopyrightConfigurable(myProject, clone, TREE_UPDATER, shareProfile)), myRoot);
    }
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
