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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeyboardSettingsExternalizable;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.keymap.impl.SystemShortcuts;
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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.ToggleActionButton;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.ui.mac.touchbar.Utils;
import com.intellij.util.Alarm;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
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

  private ShowFNKeysSettingWrapper myShowFN;

  private final @NotNull SystemShortcuts mySystemShortcuts = new SystemShortcuts();
  private boolean myShowOnlyConflicts;
  private JPanel mySystemShortcutConflictsPanel;
  private ToggleActionButton myShowOnlyConflictsButton;

  public KeymapPanel() { this(false); }

  public KeymapPanel(boolean showOnlyConflicts) {
    myShowOnlyConflicts = showOnlyConflicts;
    setLayout(new BorderLayout());
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.add(myManager.getSchemesPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);

    IdeFrame ideFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (ideFrame != null && KeyboardSettingsExternalizable.isSupportedKeyboardLayout(ideFrame.getComponent())) {
      preferKeyPositionOverCharOption = new JCheckBox(new AbstractAction(KeyMapBundle.message("prefer.key.position")) {
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

    mySystemShortcutConflictsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
    add(mySystemShortcutConflictsPanel, BorderLayout.SOUTH);
  }

  private void fillConflictsPanel(@NotNull Keymap keymap) {
    mySystemShortcutConflictsPanel.removeAll();
    final @NotNull Collection<SystemShortcuts.ConflictItem> allConflicts = mySystemShortcuts.getUnmutedKeymapConflicts();
    if (allConflicts.isEmpty())
      return;

    String htmlBody = "";
    final Map<String, Runnable> href2linkAction = new HashMap<>();
    int count = 0;
    boolean empty = true;
    for (SystemShortcuts.ConflictItem conf: allConflicts) {
      final @NotNull String actId = conf.getActionIds()[0];
      href2linkAction.put(actId, ()->{
        final KeyboardShortcut confShortcut = findKeyboardShortcut(keymap, conf.getSysKeyStroke(), actId);
        addKeyboardShortcut(actId, ActionShortcutRestrictions.getInstance().getForActionId(actId), keymap, this, confShortcut, mySystemShortcuts, myQuickLists);
      });
      final AnAction act = ActionManager.getInstance().getAction(actId);
      final String actText = act == null ? actId : act.getTemplateText();
      if (!empty)
        htmlBody += ", ";
      htmlBody += "<a href='" + actId + "'>" + actText + "</a>";

      empty = false;
      ++count;
      if (count > 2)
        break;
    }

    if (count > 2 && allConflicts.size() > count) {
      final @NotNull String text = String.format("%d more", allConflicts.size() - count);
      htmlBody += " and <a href='" + text + "'>" + text + "</a>";

      href2linkAction.put(text, ()->{
        myShowOnlyConflicts = true;
        myActionsTree.setBaseFilter(mySystemShortcuts.createKeymapConflictsActionFilter());
        myActionsTree.filter(null, myQuickLists);
        TreeUtil.expandAll(myActionsTree.getTree());
      });
    }

    htmlBody += " shortcuts conflict with macOS system shortcuts.<br>Modify shortcuts or change macOS system settings.</p></html>";

    JBLabel jbLabel = new JBLabel(createWarningHtmlText(htmlBody)) {
      @Override
      protected HyperlinkListener createHyperlinkListener() {
        return new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              final String href = e.getDescription();
              final Runnable act = href2linkAction.get(href);
              if (act != null)
                act.run();
            }
          }
        };
      }
    };
    jbLabel.setCopyable(true);
    jbLabel.setAllowAutoWrapping(true);
    jbLabel.setIconWithAligment(AllIcons.General.Warning, JLabel.LEFT, JLabel.TOP);
    mySystemShortcutConflictsPanel.add(jbLabel);

    validate();
    repaint();
  }

  private static String createWarningHtmlText(@Nullable String htmlBody) {
    if (htmlBody == null)
      return null;

    final String css = "<head><style type=\"text/css\">\n" +
                 "a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkColor()) + ";}\n" +
                 "a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkVisitedColor()) + ";}\n" +
                 "a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkHoverColor()) + ";}\n" +
                 "a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.linkPressedColor()) + ";}\n" +
                 "</style>\n</head>";
    return String.format("<html>" + css + "<body><div>%s</div></body></html>", htmlBody);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myFilteringPanel != null) {
      SwingUtilities.updateComponentTreeUI(myFilteringPanel);
    }
  }

  @Override
  public void quickListRenamed(@NotNull final QuickList oldQuickList, @NotNull final QuickList newQuickList) {
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
    mySystemShortcuts.updateKeymapConflicts(selectedKeymap);
    myShowOnlyConflictsButton.setVisible(!mySystemShortcuts.getUnmutedKeymapConflicts().isEmpty());
    myActionsTree.setBaseFilter(myShowOnlyConflicts ? mySystemShortcuts.createKeymapConflictsActionFilter() : null);
    myActionsTree.reset(selectedKeymap, myQuickLists);
    fillConflictsPanel(selectedKeymap);
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

    if (TouchBarsManager.isTouchBarAvailable()) {
      myShowFN = new ShowFNKeysSettingWrapper();
      if (myShowFN.getCheckbox() != null)
        panel.add(myShowFN.getCheckbox(), BorderLayout.SOUTH);
      Disposer.register(this, myShowFN);
    }

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

    group.add(new AnAction("Edit Shortcut", "Edit Shortcut", AllIcons.Actions.Edit) {
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

    myShowOnlyConflictsButton = new ToggleActionButton("Show conflicts with system shortcuts", AllIcons.General.ShowWarning) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myShowOnlyConflicts;
      }
      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myShowOnlyConflicts = state;
        myActionsTree.setBaseFilter(myShowOnlyConflicts ? mySystemShortcuts.createKeymapConflictsActionFilter() : null);
        myActionsTree.filter(null, myQuickLists);
        final JTree tree = myActionsTree.getTree();
        if (myShowOnlyConflicts) {
          TreeUtil.expandAll(tree);
        } else {
          TreeUtil.collapseAll(tree, 0);
        }
      }
    };
    group.add(myShowOnlyConflictsButton);

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
      public void update(@NotNull AnActionEvent event) {
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
    addKeyboardShortcut(actionId, restrictions, keymapSelected, parent, null, null, quickLists);
  }

  public static void addKeyboardShortcut(@NotNull String actionId,
                                         @NotNull ShortcutRestrictions restrictions,
                                         @NotNull Keymap keymapSelected,
                                         @NotNull Component parent,
                                         @Nullable KeyboardShortcut selectedShortcut,
                                         @Nullable SystemShortcuts systemShortcuts,
                                         @NotNull QuickList... quickLists) {
    if (!restrictions.allowKeyboardShortcut) return;
    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(parent, restrictions.allowKeyboardSecondStroke, systemShortcuts == null ? null : systemShortcuts.createKeystroke2SysShortcutMap());
    KeyboardShortcut keyboardShortcut = dialog.showAndGet(actionId, keymapSelected, selectedShortcut, quickLists);
    if (keyboardShortcut == null) return;

    SafeKeymapAccessor accessor = new SafeKeymapAccessor(parent, keymapSelected);
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        Keymap keymap = accessor.keymap();
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
    if (systemShortcuts != null) { // check conflicts with system shortcuts
      final Keymap keymap = accessor.keymap();
      final Map<KeyboardShortcut, String> kscs = systemShortcuts.calculateConflicts(keymap, actionId);
      if (kscs != null && !kscs.isEmpty()) {
        for (KeyboardShortcut ksc : kscs.keySet()) {
          String keyDesc = StringUtil.notNullize(KeymapUtil.getKeystrokeText(ksc.getFirstKeyStroke()));
          if (ksc.getSecondKeyStroke() != null)
            keyDesc += ", " + KeymapUtil.getKeystrokeText(ksc.getSecondKeyStroke());
          final int result = Messages.showYesNoCancelDialog(
            parent,
            "Action shortcut " + keyDesc + " is already assigned to system action '" + kscs.get(ksc) + "' . Do you want to remove this shortcut?",
            KeyMapBundle.message("conflict.shortcut.dialog.title"),
            KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
            KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
            KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
            Messages.getWarningIcon());
          if (result == Messages.YES) {
            keymap.removeShortcut(actionId, ksc);
          }
        }
      }
    }
    accessor.add(actionId, keyboardShortcut);
    if (systemShortcuts != null)
      systemShortcuts.updateKeymapConflicts(accessor.keymap());
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

    SafeKeymapAccessor accessor = new SafeKeymapAccessor(parent, keymapSelected);
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        Keymap keymap = accessor.keymap();
        String[] actionIds = keymap.getActionIds(mouseShortcut);
        for (String id : actionIds) {
          keymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != Messages.NO) {
        return;
      }
    }
    accessor.add(actionId, mouseShortcut);
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
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

    if (myShowFN != null)
      myShowFN.applyChanges();
  }

  @Override
  public boolean isModified() {
    return myManager.isModified() || (myShowFN != null && myShowFN.isModified());
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
          addKeyboardShortcut(actionId, restrictions, selectedKeymap, KeymapPanel.this, null, mySystemShortcuts, myQuickLists);
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

  private static class ShowFNKeysSettingWrapper implements Disposable {
    private boolean myShowFnInitial = false;
    private JCheckBox myCheckbox = null;
    private volatile boolean myDisposed;

    ShowFNKeysSettingWrapper() {
      if (TouchBarsManager.isTouchBarAvailable()) {
        final String appId = Utils.getAppId();
        if (appId != null && !appId.isEmpty()) {
          myShowFnInitial = NSDefaults.isShowFnKeysEnabled(appId);
          myCheckbox = new JCheckBox("Show F1, F2, etc. keys on the Touch Bar (requires restart)", myShowFnInitial);
        } else
          Logger.getInstance(KeymapPanel.class).error("can't obtain application id from NSBundle");
      }
    }

    JCheckBox getCheckbox() { return myCheckbox; }

    boolean isModified() { return myCheckbox == null ? false : myShowFnInitial != myCheckbox.isSelected(); }

    void applyChanges() {
      if (!TouchBarsManager.isTouchBarAvailable() || myCheckbox == null || !isModified())
        return;

      final String appId = Utils.getAppId();
      if (appId == null || appId.isEmpty()) {
        Logger.getInstance(KeymapPanel.class).error("can't obtain application id from NSBundle");
        return;
      }

      final boolean prevVal = myShowFnInitial;
      myShowFnInitial = myCheckbox.isSelected();
      NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial);

      if (myShowFnInitial != NSDefaults.isShowFnKeysEnabled(appId)) {
        NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial, true); // try again with extra checks
        if (myShowFnInitial != NSDefaults.isShowFnKeysEnabled(appId))
          return;
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final boolean result = Utils.restartTouchBarServer();
        if (!result) {
          // System.out.println("can't restart touchbar-server, roll back settings");
          myShowFnInitial = prevVal;
          NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial);

          if (!myDisposed) {
            // System.out.println("ui wasn't disposed, invoke roll back of checkbox state");
            ApplicationManager.getApplication().invokeLater(() -> {
              if (!myDisposed)
                myCheckbox.setSelected(prevVal);
            }, ModalityState.stateForComponent(myCheckbox));
          }
        }
      });
    }

    @Override
    public void dispose() {
      if (!myDisposed) {
        myDisposed = true;
        myCheckbox = null;
      }
    }
  }

  private static final class SafeKeymapAccessor {
    private final Component parent;
    private final Keymap selected;
    private KeymapSchemeManager manager;
    private Keymap mutable;

    SafeKeymapAccessor(@NotNull Component parent, @NotNull Keymap selected) {
      this.parent = parent;
      this.selected = selected;
    }

    Keymap keymap() {
      if (mutable == null) {
        if (parent instanceof KeymapPanel) {
          KeymapPanel panel = (KeymapPanel)parent;
          mutable = panel.myManager.getMutableKeymap(selected);
        }
        else {
          if (manager == null) {
            manager = new KeymapSelector(selectedKeymap -> {
            }).getManager();
            manager.reset();
          }
          mutable = manager.getMutableKeymap(selected);
        }
      }
      return mutable;
    }

    void add(@NotNull String actionId, @NotNull Shortcut newShortcut) {
      Keymap keymap = keymap();
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut.equals(newShortcut)) {
          // if shortcut is already registered to this action, just select it
          if (manager != null) manager.apply();
          return;
        }
      }
      keymap.addShortcut(actionId, newShortcut);
      if (StringUtil.startsWithChar(actionId, '$')) {
        keymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), newShortcut);
      }
      if (manager != null) manager.apply();
    }
  }
  private static @Nullable KeyboardShortcut findKeyboardShortcut(@NotNull Keymap keymap, @NotNull KeyStroke ks, @NotNull String actionId) {
    final Shortcut[] actionShortcuts = keymap.getShortcuts(actionId);
    if (actionShortcuts == null || actionShortcuts.length == 0)
      return null;

    for (Shortcut sc: actionShortcuts) {
      if (!(sc instanceof KeyboardShortcut))
        continue;
      final KeyboardShortcut ksc = (KeyboardShortcut)sc;
      if (ks.equals(ksc.getFirstKeyStroke()) || ks.equals(ksc.getSecondKeyStroke())) {
        return ksc;
      }
    }
    return null;
  }
}
