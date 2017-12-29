/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.diagnostic.VMOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeyboardSettingsExternalizable;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.updateAllToolbarsImmediately;

public class KeymapPanel extends JPanel implements SearchableConfigurable, Configurable.NoScroll, KeymapListener, Disposable {
  private JCheckBox preferKeyPositionOverCharOption;

  private final KeymapSchemeManager myManager = new KeymapSelector(this::currentKeymapChanged).getManager();
  private final ActionsTree myActionsTree = new ActionsTree();
  private FilterComponent myFilterComponent;
  private TreeExpansionMonitor myTreeExpansionMonitor;
  private final ShortcutFilteringPanel myFilteringPanel = new ShortcutFilteringPanel();

  private boolean myQuickListsModified = false;
  private QuickList[] myQuickLists = QuickListsManager.getInstance().getAllQuickLists();

  public KeymapPanel() {
    setLayout(new BorderLayout());
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.add(myManager.getSchemesPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);

    IdeFrame ideFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (ideFrame != null && KeyboardSettingsExternalizable.isSupportedKeyboardLayout(ideFrame.getComponent())) {
      preferKeyPositionOverCharOption = new JCheckBox(new AbstractAction(" " + KeyMapBundle.message("prefer.key.position")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          KeyboardSettingsExternalizable.getInstance().setPreferKeyPositionOverCharOption(preferKeyPositionOverCharOption.isSelected());
          VMOptions.writeOption("com.jetbrains.use.old.keyevent.processing", "=",
                                Boolean.toString(KeyboardSettingsExternalizable.getInstance().isPreferKeyPositionOverCharOption()));
          ApplicationManager.getApplication().invokeLater(
            () -> ApplicationManager.getApplication().restart(),
            ModalityState.NON_MODAL
          );
        }
      });
      preferKeyPositionOverCharOption.setBorder(JBUI.Borders.empty());
      keymapPanel.add(preferKeyPositionOverCharOption, BorderLayout.SOUTH);
    }

    add(keymapPanel, BorderLayout.CENTER);
    addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(@NotNull final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals("ancestor") && evt.getNewValue() != null && evt.getOldValue() == null && myQuickListsModified) {
          currentKeymapChanged();
          myQuickListsModified = false;
        }
      }
    });
    myFilteringPanel.addPropertyChangeListener("shortcut", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        filterTreeByShortcut(myFilteringPanel.getShortcut());
      }
    });
    //ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(CHANGE_TOPIC, this);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myFilteringPanel != null) {
      SwingUtilities.updateComponentTreeUI(myFilteringPanel);
    }
  }

  @Override
  public void quickListRenamed(final QuickList oldQuickList, final QuickList newQuickList) {
    myManager.visitMutableKeymaps(keymap -> {
      String actionId = oldQuickList.getActionId();
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      if (shortcuts.length != 0) {
        String newActionId = newQuickList.getActionId();
        for (Shortcut shortcut : shortcuts) {
          keymap.removeShortcut(actionId, shortcut);
          keymap.addShortcut(newActionId, shortcut);
        }
      }
    });
    myQuickListsModified = true;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return () -> showOption(option);
  }

  @Override
  public void processCurrentKeymapChanged() {
    currentKeymapChanged();
  }

  @Override
  public void processCurrentKeymapChanged(@NotNull QuickList[] ids) {
    myQuickLists = ids;
    currentKeymapChanged();
  }

  private void currentKeymapChanged() {
    currentKeymapChanged(myManager.getSelectedKeymap());
  }

  private void currentKeymapChanged(Keymap selectedKeymap) {
    if (selectedKeymap == null) selectedKeymap = new KeymapImpl();
    myActionsTree.reset(selectedKeymap, myQuickLists);
  }

  private JPanel createKeymapSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(createToolbarPanel(), BorderLayout.NORTH);
    panel.add(myActionsTree.getComponent(), BorderLayout.CENTER);

    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        editSelection(e, true);
        return true;
      }
    }.installOn(myActionsTree.getTree());


    myActionsTree.getTree().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
          editSelection(e, false);
          e.consume();
        }
      }

      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
          editSelection(e, false);
          e.consume();
        }
      }
    });
    return panel;
  }

  private JPanel createToolbarPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    DefaultActionGroup group = new DefaultActionGroup();
    final JComponent toolbar = ActionManager.getInstance().createActionToolbar("KeymapEdit", group, true).getComponent();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = createTreeExpander(myActionsTree);
    group.add(commonActionsManager.createExpandAllAction(treeExpander, myActionsTree.getTree()));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander, myActionsTree.getTree()));

    group.add(new AnAction("Edit Shortcut", "Edit Shortcut", AllIcons.ToolbarDecorator.Edit) {
      {
        registerCustomShortcutSet(CommonShortcuts.ENTER, myActionsTree.getTree());
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        final String actionId = myActionsTree.getSelectedActionId();
        e.getPresentation().setEnabled(actionId != null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        editSelection(e.getInputEvent(), false);
      }
    });

    panel.add(toolbar, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsTop(8), 0, 0));
    group = new DefaultActionGroup();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("Keymap", group, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent searchToolbar = actionToolbar.getComponent();
    final Alarm alarm = new Alarm();
    myFilterComponent = new FilterComponent("KEYMAP", 5) {
      @Override
      public void filter() {
        alarm.cancelAllRequests();
        alarm.addRequest(() -> {
          if (!myFilterComponent.isShowing()) return;
          myTreeExpansionMonitor.freeze();
          myFilteringPanel.setShortcut(null);
          final String filter = getFilter();
          myActionsTree.filter(filter, myQuickLists);
          final JTree tree = myActionsTree.getTree();
          TreeUtil.expandAll(tree);
          if (filter == null || filter.length() == 0) {
            TreeUtil.collapseAll(tree, 0);
            myTreeExpansionMonitor.restore();
          }
          else {
            myTreeExpansionMonitor.unfreeze();
          }
        }, 300);
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE,
                                                        JBUI.insetsTop(8), 0, 0));

    group.add(new DumbAwareAction(KeyMapBundle.message("filter.shortcut.action.text"),
                                  KeyMapBundle.message("filter.shortcut.action.text"),
                                  AllIcons.Actions.ShortcutFilter) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myFilterComponent.reset();
        currentKeymapChanged();
        myFilteringPanel.showPopup(searchToolbar, e.getInputEvent().getComponent());
      }
    });
    group.add(new DumbAwareAction(KeyMapBundle.message("filter.clear.action.text"),
                                  KeyMapBundle.message("filter.clear.action.text"), AllIcons.Actions.GC) {
      @Override
      public void update(AnActionEvent event) {
        boolean enabled = null != myFilteringPanel.getShortcut();
        Presentation presentation = event.getPresentation();
        presentation.setEnabled(enabled);
        presentation.setIcon(enabled ? AllIcons.Actions.Cancel : EmptyIcon.ICON_16);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTreeExpansionMonitor.freeze();
        myFilteringPanel.setShortcut(null);
        myActionsTree.filter(null, myQuickLists); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, JBUI.insetsTop(8), 0, 0));
    return panel;
  }

  @NotNull
  public static TreeExpander createTreeExpander(ActionsTree actionsTree) {
    return new TreeExpander() {
      @Override
      public void expandAll() {
        TreeUtil.expandAll(actionsTree.getTree());
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(actionsTree.getTree(), 0);
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    };
  }

  private void filterTreeByShortcut(Shortcut shortcut) {
    boolean wasFreezed = myTreeExpansionMonitor.isFreeze();
    if (!wasFreezed) myTreeExpansionMonitor.freeze();
    myActionsTree.filterTree(shortcut, myQuickLists);
    final JTree tree = myActionsTree.getTree();
    TreeUtil.expandAll(tree);
    if (!wasFreezed) myTreeExpansionMonitor.restore();
  }

  public void showOption(String option) {
    currentKeymapChanged();
    myFilterComponent.setFilter(option);
    myFilteringPanel.setShortcut(null);
    myActionsTree.filter(option, myQuickLists);
  }

  public static void addKeyboardShortcut(@NotNull String actionId,
                                         @NotNull ShortcutRestrictions restrictions,
                                         @NotNull Keymap keymapSelected,
                                         @NotNull Component parent,
                                         @NotNull QuickList... quickLists) {
    if (!restrictions.allowKeyboardShortcut) return;
    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(parent, restrictions.allowKeyboardSecondStroke);
    KeyboardShortcut keyboardShortcut = dialog.showAndGet(actionId, keymapSelected, quickLists);
    if (keyboardShortcut == null) return;

    Keymap keymap = null;
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        keymap = createKeymapCopyIfNeededAndPossible(parent, keymapSelected);
        Map<String, List<KeyboardShortcut>> conflicts = keymap.getConflicts(actionId, keyboardShortcut);
        for (String id : conflicts.keySet()) {
          for (KeyboardShortcut s : conflicts.get(id)) {
            keymap.removeShortcut(id, s);
          }
        }
      }
      else if (result != Messages.NO) {
        return;
      }
    }

    // if shortcut is already registered to this action, just select it in the list

    if (keymap == null) keymap = createKeymapCopyIfNeededAndPossible(parent, keymapSelected);
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    for (Shortcut s : shortcuts) {
      if (s.equals(keyboardShortcut)) {
        return;
      }
    }

    keymap.addShortcut(actionId, keyboardShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      keymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), keyboardShortcut);
    }
  }

  private static void addMouseShortcut(@NotNull String actionId,
                                       @NotNull ShortcutRestrictions restrictions,
                                       @NotNull Keymap keymapSelected,
                                       @NotNull Component parent,
                                       @NotNull QuickList... quickLists) {
    if (!restrictions.allowMouseShortcut) return;
    MouseShortcutDialog dialog = new MouseShortcutDialog(parent, restrictions.allowMouseDoubleClick);
    MouseShortcut mouseShortcut = dialog.showAndGet(actionId, keymapSelected, quickLists);
    if (mouseShortcut == null) return;

    Keymap keymap = null;
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        keymap = createKeymapCopyIfNeededAndPossible(parent, keymapSelected);
        String[] actionIds = keymap.getActionIds(mouseShortcut);
        for (String id : actionIds) {
          keymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != Messages.NO) {
        return;
      }
    }

    // if shortcut is already registered to this action, just select it in the list

    if (keymap == null) keymap = createKeymapCopyIfNeededAndPossible(parent, keymapSelected);
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    for (Shortcut shortcut1 : shortcuts) {
      if (shortcut1.equals(mouseShortcut)) {
        return;
      }
    }

    keymap.addShortcut(actionId, mouseShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      keymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), mouseShortcut);
    }
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
  }

  @NotNull
  private static Keymap createKeymapCopyIfNeededAndPossible(Component parent, Keymap keymap) {
    if (parent instanceof KeymapPanel) {
      KeymapPanel panel = (KeymapPanel)parent;
      return panel.myManager.getMutableKeymap(keymap);
    }
    return keymap;
  }

  @Override
  @NotNull
  public String getId() {
    return "preferences.keymap";
  }

  @Override
  public void reset() {
    if (preferKeyPositionOverCharOption != null) {
      preferKeyPositionOverCharOption.setSelected(KeyboardSettingsExternalizable.getInstance().isPreferKeyPositionOverCharOption());
    }
    myManager.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    String error = myManager.apply();
    if (error != null) throw new ConfigurationException(error);
    updateAllToolbarsImmediately();
  }

  @Override
  public boolean isModified() {
    return myManager.isModified();
  }

  public void selectAction(String actionId) {
    myActionsTree.selectAction(actionId);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.keymap";
  }

  @Override
  public JComponent createComponent() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(CHANGE_TOPIC, this);
    return this;
  }

  @Override
  public void disposeUIResources() {
    myFilteringPanel.hidePopup();
    if (myFilterComponent != null) {
      myFilterComponent.dispose();
    }
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public Shortcut[] getCurrentShortcuts(@NotNull String actionId) {
    Keymap keymap = myManager.getSelectedKeymap();
    return keymap == null ? null : keymap.getShortcuts(actionId);
  }

  private void editSelection(InputEvent e, boolean isDoubleClick) {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) return;

    Keymap selectedKeymap = myManager.getSelectedKeymap();
    if (selectedKeymap == null) return;

    DefaultActionGroup group = createEditActionGroup(actionId, selectedKeymap);
    if (e instanceof MouseEvent && ((MouseEvent)e).isPopupTrigger()) {
      ActionManager.getInstance()
        .createActionPopupMenu(ActionPlaces.UNKNOWN, group)
        .getComponent()
        .show(e.getComponent(), ((MouseEvent)e).getX(), ((MouseEvent)e).getY());
    }
    else if (!isDoubleClick || !ActionManager.getInstance().isGroup(actionId)) {
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Edit Shortcuts",
                                                                            group,
                                                                            dataContext,
                                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                            true);

      if (e instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e));
      }
      else {
        popup.showInBestPositionFor(dataContext);
      }
    }
  }

  @NotNull
  private DefaultActionGroup createEditActionGroup(@NotNull String actionId, Keymap selectedKeymap) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ShortcutRestrictions restrictions = ActionShortcutRestrictions.getInstance().getForActionId(actionId);
    if (restrictions.allowKeyboardShortcut) {
      group.add(new DumbAwareAction("Add Keyboard Shortcut") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          addKeyboardShortcut(actionId, restrictions, selectedKeymap, KeymapPanel.this, myQuickLists);
          currentKeymapChanged();
        }
      });
    }

    if (restrictions.allowMouseShortcut) {
      group.add(new DumbAwareAction("Add Mouse Shortcut") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          addMouseShortcut(actionId, restrictions, selectedKeymap, KeymapPanel.this, myQuickLists);
          currentKeymapChanged();
        }
      });
    }

    if (Registry.is("actionSystem.enableAbbreviations") && restrictions.allowAbbreviation) {
      group.add(new DumbAwareAction("Add Abbreviation") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String abbr = Messages.showInputDialog("Enter new abbreviation:", "Abbreviation", null);
          if (abbr != null) {
            AbbreviationManager.getInstance().register(abbr, actionId);
            repaintLists();
          }
        }
      });
    }

    group.addSeparator();

    for (Shortcut shortcut : selectedKeymap.getShortcuts(actionId)) {
      group.add(new DumbAwareAction("Remove " + KeymapUtil.getShortcutText(shortcut)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Keymap keymap = myManager.getMutableKeymap(selectedKeymap);
          keymap.removeShortcut(actionId, shortcut);
          if (StringUtil.startsWithChar(actionId, '$')) {
            keymap.removeShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), shortcut);
          }
          currentKeymapChanged();
        }
      });
    }

    if (Registry.is("actionSystem.enableAbbreviations")) {
      for (final String abbreviation : AbbreviationManager.getInstance().getAbbreviations(actionId)) {
        group.addAction(new DumbAwareAction("Remove Abbreviation '" + abbreviation + "'") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            AbbreviationManager.getInstance().remove(abbreviation, actionId);
            repaintLists();
          }
        });
      }
    }
    if (myManager.canResetActionInKeymap(selectedKeymap, actionId)) {
      group.add(new Separator());
      group.add(new DumbAwareAction("Reset Shortcuts") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          myManager.resetActionInKeymap(selectedKeymap, actionId);
          repaintLists();
        }
      });
    }
    return group;
  }

  private static int showConfirmationDialog(Component parent) {
    return Messages.showYesNoCancelDialog(
      parent,
      KeyMapBundle.message("conflict.shortcut.dialog.message"),
      KeyMapBundle.message("conflict.shortcut.dialog.title"),
      KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
      Messages.getWarningIcon());
  }
}
