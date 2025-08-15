// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.diagnostic.VMOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.*;
import com.intellij.openapi.keymap.impl.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.ui.mac.touchbar.Helpers;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.IoErrorText;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
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
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.updateAllToolbarsImmediately;

public final class KeymapPanel extends JPanel implements SearchableConfigurable, Configurable.NoScroll, KeymapListener, SystemShortcutsListener, Disposable {
  private JCheckBox nationalKeyboardsSupport;

  private final KeymapSelector myKeymapSelector = new KeymapSelector(this::currentKeymapChanged);
  private final KeymapSchemeManager myManager = myKeymapSelector.getManager();
  private final ActionsTree myActionsTree = new ActionsTree();
  private FilterComponent myFilterComponent;
  private TreeExpansionMonitor myTreeExpansionMonitor;
  private final @NotNull ShortcutFilteringPanel myFilteringPanel = new ShortcutFilteringPanel();

  private boolean myQuickListsModified = false;
  private QuickList[] myQuickLists = QuickListsManager.getInstance().getAllQuickLists();

  private ShowFNKeysSettingWrapper myShowFN;

  private boolean myShowOnlyConflicts;
  private final JPanel mySystemShortcutConflictsPanel;

  public KeymapPanel() { this(false); }

  public KeymapPanel(boolean showOnlyConflicts) {
    myShowOnlyConflicts = showOnlyConflicts;
    setLayout(new BorderLayout());
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.add(myManager.getSchemesPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);

    IdeFrame ideFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (ideFrame != null && NationalKeyboardSupport.isSupportedKeyboardLayout(ideFrame.getComponent())) {
      nationalKeyboardsSupport = new JCheckBox(
        new AbstractAction(KeyMapBundle.message(NationalKeyboardSupport.getKeymapBundleKey())) {
          @Override
          public void actionPerformed(ActionEvent e) {
            NationalKeyboardSupport.getInstance().setEnabled(nationalKeyboardsSupport.isSelected());
            try {
              VMOptions.setProperty(NationalKeyboardSupport.getVMOption(), Boolean.toString(NationalKeyboardSupport.getInstance().getEnabled()));
              ApplicationManager.getApplication().invokeLater(
                () -> ApplicationManager.getApplication().restart(),
                ModalityState.nonModal()
              );
            }
            catch (IOException x) {
              Messages.showErrorDialog(keymapPanel, IoErrorText.message(x), OptionsBundle.message("cannot.save.settings.default.dialog.title"));
            }
          }
        });
      nationalKeyboardsSupport.setSelected(NationalKeyboardSupport.getInstance().getEnabled());
      nationalKeyboardsSupport.setBorder(JBUI.Borders.empty());
      keymapPanel.add(nationalKeyboardsSupport, BorderLayout.SOUTH);
    }

    add(keymapPanel, BorderLayout.CENTER);
    addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(final @NotNull PropertyChangeEvent evt) {
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
    SystemShortcuts systemShortcuts = SystemShortcuts.getInstance();
    final @NotNull Collection<SystemShortcuts.ConflictItem> allConflicts = systemShortcuts.getUnmutedKeymapConflicts();
    if (allConflicts.isEmpty())
      return;

    HtmlBuilder links = new HtmlBuilder();
    final Map<String, Runnable> href2linkAction = new HashMap<>();
    int count = 0;
    boolean empty = true;
    for (SystemShortcuts.ConflictItem conf: allConflicts) {
      final @NotNull String actId = conf.getActionIds()[0];
      href2linkAction.put(actId, ()->{
        final KeyboardShortcut confShortcut = findKeyboardShortcut(keymap, conf.getSysKeyStroke(), actId);
        addKeyboardShortcut(actId, ActionShortcutRestrictions.getInstance().getForActionId(actId), keymap, this, confShortcut,
                            systemShortcuts, myQuickLists);
      });
      AnAction action = ActionManager.getInstance().getAction(actId);
      String actionText = action == null ? null : action.getTemplateText();
      if (!empty) links.append(", ");
      links.appendLink(actId, StringUtil.notNullize(actionText, actId));

      empty = false;
      ++count;
      if (count > 2)
        break;
    }

    String shortcutsMessage;
    if (count > 2 && allConflicts.size() > count) {
      String actionId = "show.more";
      HtmlChunk.Element moreLink = HtmlChunk.link(actionId, IdeBundle.message("more.shortcuts.link.text.with.count", allConflicts.size() - count));

      href2linkAction.put(actionId, ()->{
        myShowOnlyConflicts = true;
        myActionsTree.setBaseFilter(systemShortcuts.createKeymapConflictsActionFilter());
        myActionsTree.filter(null, myQuickLists);
        TreeUtil.expandAll(myActionsTree.getTree());
      });
      shortcutsMessage = IdeBundle.message("macos.shortcut.conflict.many", links, moreLink);
    } else {
      shortcutsMessage = IdeBundle.message("macos.shortcut.conflict.few", links);
    }

    HtmlBuilder builder = new HtmlBuilder();
    builder.appendRaw(shortcutsMessage)
      .br().append(IdeBundle.message("assign.custom.shortcuts.or.change.the.macos.system.settings"));

    JBLabel jbLabel = new JBLabel(createWarningHtmlText(builder.toString())) {
      @Override
      protected @NotNull HyperlinkListener createHyperlinkListener() {
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
    jbLabel.setIconWithAlignment(AllIcons.General.Warning, SwingConstants.LEFT, SwingConstants.TOP);
    mySystemShortcutConflictsPanel.add(jbLabel);

    validate();
    repaint();
  }

  private static @Nls String createWarningHtmlText(@Nullable @Nls String htmlBody) {
    if (htmlBody == null)
      return null;

    return new HtmlBuilder()
      .append(HtmlChunk.head().child(HtmlChunk.styleTag(
        "a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}\n" +
        "a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED) + ";}\n" +
        "a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}\n" +
        "a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}\n")))
      .append(HtmlChunk.body().child(HtmlChunk.div().addRaw(htmlBody)))
      .wrapWith("html")
      .toString();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    //noinspection ConstantValue -- can be called during superclass initialization
    if (myFilteringPanel != null) {
      SwingUtilities.updateComponentTreeUI(myFilteringPanel);
    }
  }

  @Override
  public void quickListRenamed(final @NotNull QuickList oldQuickList, final @NotNull QuickList newQuickList) {
    myManager.visitMutableKeymaps(keymap -> {
      String actionId = oldQuickList.getActionId();
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      if (shortcuts.length != 0) {
        String newActionId = newQuickList.getActionId();
        for (Shortcut shortcut : shortcuts) {
          removeShortcut(keymap, actionId, shortcut);
          addShortcut(keymap, newActionId, shortcut);
        }
      }
    });
    myQuickListsModified = true;
  }

  private static void addShortcut(Keymap keymap, String actionId, Shortcut shortcut) {
    if (keymap instanceof KeymapImpl) {
      ((KeymapImpl)keymap).addShortcutFromSettings(actionId, shortcut);
    }
    else {
      keymap.addShortcut(actionId, shortcut);
    }
  }

  private static void removeShortcut(Keymap keymap, String actionId, Shortcut shortcut) {
    if (keymap instanceof KeymapImpl) {
      ((KeymapImpl)keymap).removeShortcutFromSettings(actionId, shortcut);
    }
    else {
      keymap.removeShortcut(actionId, shortcut);
    }
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
  public void processCurrentKeymapChanged(QuickList @NotNull [] ids) {
    myQuickLists = ids;
    currentKeymapChanged();
  }

  @Override
  public void processSystemShortcutsChanged() {
    var selectedKeymap = myManager.getSelectedKeymap();
    if (selectedKeymap != null) {
      fillConflictsPanel(selectedKeymap);
    }
  }

  private void currentKeymapChanged() {
    currentKeymapChanged(myManager.getSelectedKeymap());
  }

  private void currentKeymapChanged(Keymap selectedKeymap) {
    if (selectedKeymap == null) selectedKeymap = new KeymapImpl();
    SystemShortcuts systemShortcuts = SystemShortcuts.getInstance();
    systemShortcuts.updateKeymapConflicts(selectedKeymap);
    myActionsTree.setBaseFilter(myShowOnlyConflicts ? systemShortcuts.createKeymapConflictsActionFilter() : null);
    myActionsTree.reset(selectedKeymap, myQuickLists, myFilteringPanel.getShortcut());
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
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
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

    if (TouchbarSupport.isAvailable()) {
      myShowFN = new ShowFNKeysSettingWrapper();
      if (myShowFN.getCheckbox() != null) {
        panel.add(myShowFN.getCheckbox(), BorderLayout.SOUTH);
      }
    }

    return panel;
  }

  private JPanel createToolbarPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("KeymapEdit", group, true);
    toolbar.setTargetComponent(myActionsTree.getTree());
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = createTreeExpander(myActionsTree);
    group.add(commonActionsManager.createExpandAllAction(treeExpander, myActionsTree.getTree()));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander, myActionsTree.getTree()));

    group.add(new EditShortcutAction());

    AnAction showConflictsAction = new DumbAwareToggleAction(KeyMapBundle.messagePointer("keymap.show.system.conflicts"),
                                                             Presentation.NULL_STRING, AllIcons.General.ShowWarning) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(!SystemShortcuts.getInstance().getUnmutedKeymapConflicts().isEmpty());
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return myShowOnlyConflicts;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myShowOnlyConflicts = state;
        myActionsTree.setBaseFilter(myShowOnlyConflicts ? SystemShortcuts.getInstance().createKeymapConflictsActionFilter() : null);
        myActionsTree.filter(null, myQuickLists);
        final JTree tree = myActionsTree.getTree();
        if (myShowOnlyConflicts) {
          TreeUtil.expandAll(tree);
        }
        else {
          TreeUtil.collapseAll(tree, 0);
        }
      }
    };
    group.add(showConflictsAction);

    group = new DefaultActionGroup();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("Keymap", group, true);
    actionToolbar.setTargetComponent(myActionsTree.getTree());
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent searchToolbar = actionToolbar.getComponent();
    SingleEdtTaskScheduler alarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
    myFilterComponent = new FilterComponent("KEYMAP", 5) {
      @Override
      public void filter() {
        alarm.cancelAndRequest(300, () -> {
          if (!myFilterComponent.isShowing()) return;
          myTreeExpansionMonitor.freeze();
          myFilteringPanel.setShortcut(null);
          final String filter = getFilter();
          myActionsTree.filter(filter, myQuickLists);
          final JTree tree = myActionsTree.getTree();
          TreeUtil.expandAll(tree);
          if (filter == null || filter.isEmpty()) {
            TreeUtil.collapseAll(tree, 0);
            myTreeExpansionMonitor.restore();
          }
          else {
            myTreeExpansionMonitor.unfreeze();
          }
        });
      }
    };
    myFilterComponent.reset();

    group.add(new FindByShortcutAction(searchToolbar));

    group.add(new ClearFilteringAction());

    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(toolbar.getComponent());
    panel.add(new BorderLayoutPanel().addToCenter(myFilterComponent).addToRight(searchToolbar));
    return panel;
  }

  public static @NotNull TreeExpander createTreeExpander(@NotNull ActionsTree actionsTree) {
    return new DefaultTreeExpander(actionsTree::getTree);
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
                                         QuickList @NotNull ... quickLists) {
    addKeyboardShortcut(actionId, restrictions, keymapSelected, parent, null, null, quickLists);
  }

  public static void addKeyboardShortcut(@NotNull String actionId,
                                         @NotNull ShortcutRestrictions restrictions,
                                         @NotNull Keymap keymapSelected,
                                         @NotNull Component parent,
                                         @Nullable KeyboardShortcut selectedShortcut,
                                         @Nullable SystemShortcuts systemShortcuts,
                                         QuickList @NotNull ... quickLists) {
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
            IdeBundle.message("message.action.remove.system.assigned.shortcut", keyDesc, kscs.get(ksc)),
            KeyMapBundle.message("conflict.shortcut.dialog.title"),
            KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
            KeyMapBundle.message("conflict.shortcut.dialog.keep.button"),
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
                                       QuickList @NotNull ... quickLists) {
    if (!restrictions.allowMouseShortcut) return;
    MouseShortcutDialog dialog = new MouseShortcutDialog(parent, restrictions.allowMouseDoubleClick);
    MouseShortcut mouseShortcut = dialog.showAndGet(actionId, keymapSelected, quickLists);
    if (mouseShortcut == null) return;

    SafeKeymapAccessor accessor = new SafeKeymapAccessor(parent, keymapSelected);
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        Keymap keymap = accessor.keymap();
        for (String id : keymap.getActionIds(mouseShortcut)) {
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
  public @NotNull String getId() {
    return "preferences.keymap";
  }

  @Override
  public void reset() {
    if (nationalKeyboardsSupport != null) {
      nationalKeyboardsSupport.setSelected(NationalKeyboardSupport.getInstance().getEnabled());
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
  public @Nls String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.keymap";
  }

  @Override
  public JComponent createComponent() {
    if (myShowFN != null) {
      Disposer.register(this, myShowFN);
    }
    KeymapExtension.EXTENSION_POINT_NAME.addChangeListener(this::currentKeymapChanged, this);
    myKeymapSelector.attachKeymapListener(this);
    var messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.connect(this).subscribe(KeymapListener.CHANGE_TOPIC, this);
    messageBus.connect(this).subscribe(SystemShortcutsListener.CHANGE_TOPIC, this);
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

  public Shortcut @Nullable [] getCurrentShortcuts(@NotNull String actionId) {
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
        .createActionPopupMenu("popup@Keymap.ActionsTree.Menu", group)
        .getComponent()
        .show(e.getComponent(), ((MouseEvent)e).getX(), ((MouseEvent)e).getY());
    }
    else if (!isDoubleClick || !ActionManager.getInstance().isGroup(actionId)) {
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("popup.title.edit.shortcuts"),
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

  private @NotNull DefaultActionGroup createEditActionGroup(@NotNull String actionId, Keymap selectedKeymap) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ShortcutRestrictions restrictions = ActionShortcutRestrictions.getInstance().getForActionId(actionId);
    if (restrictions.allowKeyboardShortcut) {
      group.add(new AddKeyboardShortcutAction(actionId, restrictions, selectedKeymap));
    }

    if (restrictions.allowMouseShortcut) {
      group.add(new AddMouseShortcutAction(actionId, restrictions, selectedKeymap));
    }

    if (restrictions.allowAbbreviation) {
      group.add(new AddAbbreviationAction(actionId));
    }

    group.addSeparator();

    Shortcut[] shortcuts = selectedKeymap.getShortcuts(actionId);
    for (Shortcut shortcut : shortcuts) {
      group.add(new RemoveShortcutAction(shortcut, selectedKeymap, actionId));
    }

    for (final String abbreviation : AbbreviationManager.getInstance().getAbbreviations(actionId)) {
      group.addAction(new RemoveAbbreviationAction(abbreviation, actionId));
    }

    boolean separator = true;
    if (shortcuts.length > 2) {
      group.add(new Separator());
      group.add(new RemoveAllShortcuts(selectedKeymap, actionId));
      separator = false;
    }
    if (myManager.canResetActionInKeymap(selectedKeymap, actionId)) {
      if (separator) {
        group.add(new Separator());
      }
      group.add(new ResetShortcutsAction(selectedKeymap, actionId));
    }
    return group;
  }

  private static int showConfirmationDialog(Component parent) {
    return Messages.showYesNoCancelDialog(
      parent,
      KeyMapBundle.message("conflict.shortcut.dialog.message"),
      KeyMapBundle.message("conflict.shortcut.dialog.title"),
      KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.keep.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
      Messages.getWarningIcon());
  }

  private static final class ShowFNKeysSettingWrapper implements Disposable {
    private boolean myShowFnInitial = false;
    private JCheckBox myCheckbox = null;
    private volatile boolean myDisposed;

    ShowFNKeysSettingWrapper() {
      if (TouchbarSupport.isAvailable()) {
        final String appId = Helpers.getAppId();
        if (appId != null && !appId.isEmpty()) {
          myShowFnInitial = NSDefaults.isShowFnKeysEnabled(appId);
          myCheckbox = new JCheckBox(KeyMapBundle.message("keymap.show.f.on.touch.bar"), myShowFnInitial);
        } else
          Logger.getInstance(KeymapPanel.class).error("can't obtain application id from NSBundle");
      }
    }

    JCheckBox getCheckbox() { return myCheckbox; }

    boolean isModified() { return myCheckbox != null && myShowFnInitial != myCheckbox.isSelected(); }

    void applyChanges() {
      if (!TouchbarSupport.isAvailable() || myCheckbox == null || !isModified())
        return;

      final String appId = Helpers.getAppId();
      if (appId == null || appId.isEmpty()) {
        Logger.getInstance(KeymapPanel.class).error("can't obtain application id from NSBundle");
        return;
      }

      final boolean prevVal = myShowFnInitial;
      myShowFnInitial = myCheckbox.isSelected();
      NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial);
      TouchbarSupport.enable(!myShowFnInitial);

      if (myShowFnInitial != NSDefaults.isShowFnKeysEnabled(appId)) {
        NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial, true); // try again with extra checks
        if (myShowFnInitial != NSDefaults.isShowFnKeysEnabled(appId))
          return;
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final boolean result = Helpers.restartTouchBarServer();
        if (!result) {
          // System.out.println("can't restart touchbar-server, roll back settings");
          myShowFnInitial = prevVal;
          NSDefaults.setShowFnKeysEnabled(appId, myShowFnInitial);
          TouchbarSupport.enable(!myShowFnInitial);

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
        if (parent instanceof KeymapPanel panel) {
          mutable = panel.myManager.getMutableKeymap(selected);
        }
        else {
          if (manager == null) {
            manager = new KeymapSelector(selectedKeymap -> { }).getManager();
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
      addShortcut(keymap, actionId, newShortcut);
      if (StringUtil.startsWithChar(actionId, '$')) {
        addShortcut(keymap, KeyMapBundle.message("editor.shortcut", actionId.substring(1)), newShortcut);
      }
      if (manager != null) manager.apply();
    }
  }

  private static @Nullable KeyboardShortcut findKeyboardShortcut(@NotNull Keymap keymap, @NotNull KeyStroke ks, @NotNull String actionId) {
    Shortcut[] actionShortcuts = keymap.getShortcuts(actionId);

    for (Shortcut sc: actionShortcuts) {
      if (!(sc instanceof KeyboardShortcut ksc))
        continue;
      if (ks.equals(ksc.getFirstKeyStroke()) || ks.equals(ksc.getSecondKeyStroke())) {
        return ksc;
      }
    }
    return null;
  }

  private final class EditShortcutAction extends AnAction {
    private EditShortcutAction() {
      super(KeyMapBundle.message("edit.shortcut.action.text"), KeyMapBundle.message("edit.shortcut.action.description"),
            LayeredIcon.EDIT_WITH_DROPDOWN);
      registerCustomShortcutSet(CommonShortcuts.ENTER, myActionsTree.getTree());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final String actionId = myActionsTree.getSelectedActionId();
      e.getPresentation().setEnabled(actionId != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      editSelection(e.getInputEvent(), false);
    }
  }

  private static final BadgeIconSupplier SHORTCUT_FILTER_ICON = new BadgeIconSupplier(AllIcons.Actions.ShortcutFilter);

  private final class FindByShortcutAction extends DumbAwareAction {
    private final JComponent mySearchToolbar;

    private FindByShortcutAction(JComponent searchToolbar) {
      super(KeyMapBundle.message("filter.shortcut.action.text"), KeyMapBundle.message("filter.shortcut.action.description"),
            AllIcons.Actions.ShortcutFilter);
      mySearchToolbar = searchToolbar;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(SHORTCUT_FILTER_ICON.getSuccessIcon(myFilteringPanel.getShortcut() != null));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myFilterComponent.reset();
      currentKeymapChanged();
      myFilteringPanel.showPopup(mySearchToolbar, e.getInputEvent().getComponent());
    }
  }

  private final class ClearFilteringAction extends DumbAwareAction {
    private ClearFilteringAction() {
      super(KeyMapBundle.message("filter.clear.action.text"), KeyMapBundle.message("filter.clear.action.description"), AllIcons.Actions.GC);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      boolean enabled = null != myFilteringPanel.getShortcut();
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(enabled);
      presentation.setIcon(enabled ? AllIcons.Actions.Cancel : EmptyIcon.ICON_16);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTreeExpansionMonitor.freeze();
      myFilteringPanel.setShortcut(null);
      myActionsTree.filter(null, myQuickLists); //clear filtering
      TreeUtil.collapseAll(myActionsTree.getTree(), 0);
      myTreeExpansionMonitor.restore();
    }
  }

  private final class AddKeyboardShortcutAction extends DumbAwareAction {
    private final @NotNull String myActionId;
    private final ShortcutRestrictions myRestrictions;
    private final Keymap mySelectedKeymap;

    private AddKeyboardShortcutAction(@NotNull String actionId, ShortcutRestrictions restrictions, Keymap selectedKeymap) {
      super(IdeBundle.messagePointer("action.Anonymous.text.add.keyboard.shortcut"));
      myActionId = actionId;
      myRestrictions = restrictions;
      mySelectedKeymap = selectedKeymap;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addKeyboardShortcut(myActionId, myRestrictions, mySelectedKeymap, KeymapPanel.this, null, SystemShortcuts.getInstance(), myQuickLists);
      currentKeymapChanged();
    }
  }

  private final class AddMouseShortcutAction extends DumbAwareAction {
    private final @NotNull String myActionId;
    private final ShortcutRestrictions myRestrictions;
    private final Keymap mySelectedKeymap;

    private AddMouseShortcutAction(@NotNull String actionId, ShortcutRestrictions restrictions, Keymap selectedKeymap) {
      super(IdeBundle.messagePointer("action.Anonymous.text.add.mouse.shortcut"));
      myActionId = actionId;
      myRestrictions = restrictions;
      mySelectedKeymap = selectedKeymap;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addMouseShortcut(myActionId, myRestrictions, mySelectedKeymap, KeymapPanel.this, myQuickLists);
      currentKeymapChanged();
    }
  }

  private final class AddAbbreviationAction extends DumbAwareAction {
    private final @NotNull String myActionId;

    private AddAbbreviationAction(@NotNull String actionId) {
      super(IdeBundle.messagePointer("action.Anonymous.text.add.abbreviation"));
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String abbr = Messages.showInputDialog(IdeBundle.message("label.enter.new.abbreviation"),
                                             IdeBundle.message("dialog.title.abbreviation"), null);
      if (abbr != null) {
        AbbreviationManager.getInstance().register(abbr, myActionId);
        repaintLists();
      }
    }
  }

  private final class RemoveShortcutAction extends DumbAwareAction {
    private final Shortcut myShortcut;
    private final Keymap mySelectedKeymap;
    private final @NotNull String myActionId;

    private RemoveShortcutAction(Shortcut shortcut, Keymap selectedKeymap, @NotNull String actionId) {
      super(IdeBundle.message("action.text.remove.0", KeymapUtil.getShortcutText(shortcut)));
      myShortcut = shortcut;
      mySelectedKeymap = selectedKeymap;
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Keymap keymap = myManager.getMutableKeymap(mySelectedKeymap);
      keymap.removeShortcut(myActionId, myShortcut);
      if (StringUtil.startsWithChar(myActionId, '$')) {
        keymap.removeShortcut(KeyMapBundle.message("editor.shortcut", myActionId.substring(1)), myShortcut);
      }
      currentKeymapChanged();
    }
  }

  private final class RemoveAbbreviationAction extends DumbAwareAction {
    private final String myAbbreviation;
    private final @NotNull String myActionId;

    private RemoveAbbreviationAction(String abbreviation, @NotNull String actionId) {
      super(IdeBundle.message("action.text.remove.abbreviation.0", abbreviation));
      myAbbreviation = abbreviation;
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      AbbreviationManager.getInstance().remove(myAbbreviation, myActionId);
      repaintLists();
    }
  }

  private final class ResetShortcutsAction extends DumbAwareAction {
    private final Keymap mySelectedKeymap;
    private final @NotNull String myActionId;

    private ResetShortcutsAction(Keymap selectedKeymap, @NotNull String actionId) {
      super(IdeBundle.messagePointer("action.Anonymous.text.reset.shortcuts"));
      mySelectedKeymap = selectedKeymap;
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      myManager.resetActionInKeymap(mySelectedKeymap, myActionId);
      repaintLists();
    }
  }

  private final class RemoveAllShortcuts extends DumbAwareAction {
    private final Keymap mySelectedKeymap;
    private final String myActionId;

    private RemoveAllShortcuts(Keymap selectedKeymap, @NotNull String actionId) {
      super(IdeBundle.messagePointer("action.text.remove.all.shortcuts"));
      mySelectedKeymap = selectedKeymap;
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      myManager.getMutableKeymap(mySelectedKeymap).removeAllActionShortcuts(myActionId);
      currentKeymapChanged();
    }
  }
}
