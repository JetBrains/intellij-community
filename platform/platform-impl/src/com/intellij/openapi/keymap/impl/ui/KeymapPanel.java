/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.*;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.ListItemEditor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeymapPanel extends JPanel implements SearchableConfigurable, Configurable.NoScroll, KeymapListener, Disposable {
  private static final Condition<Keymap> KEYMAP_FILTER = new Condition<Keymap>() {
    @Override
    public boolean value(Keymap keymap) {
      return !SystemInfo.isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());
    }
  };

  private final KeymapListModelEditor<Keymap> myEditor = new KeymapListModelEditor<Keymap>(new ListItemEditor<Keymap>() {
    @NotNull
    @Override
    public String getName(@NotNull Keymap item) {
      String name = item.getPresentableName();
      return name == null ? KeyMapBundle.message("keymap.noName.presentable.name") : name;
    }

    @NotNull
    @Override
    public Class<? extends Keymap> getItemClass() {
      return KeymapImpl.class;
    }

    @Override
    public Keymap clone(@NotNull Keymap item, boolean forInPlaceEditing) {
      return ((KeymapImpl)item).copy();
    }

    @Override
    public void applyModifiedProperties(@NotNull Keymap newItem, @NotNull Keymap oldItem) {
      ((KeymapImpl)newItem).copyTo((KeymapImpl)oldItem);
    }

    @Override
    public boolean isRemovable(@NotNull Keymap item) {
      return item.canModify();
    }

    @Override
    public boolean isEditable(@NotNull Keymap item) {
      return item.canModify();
    }
  });

  private JButton myCopyButton;
  private JButton myDeleteButton;
  private JButton myResetToDefault;
  private JCheckBox myNonEnglishKeyboardSupportOption;

  private JLabel myBaseKeymapLabel;

  private ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private JBPopup myPopup = null;
  private TreeExpansionMonitor myTreeExpansionMonitor;

  private boolean myQuickListsModified = false;
  private QuickList[] myQuickLists = QuickListsManager.getInstance().getAllQuickLists();

  public KeymapPanel() {
    setLayout(new BorderLayout());
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.add(createKeymapListPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);
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

    //ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(CHANGE_TOPIC, this);
  }

  @Override
  public void quickListRenamed(final QuickList oldQuickList, final QuickList newQuickList) {
    for (Keymap keymap : myEditor.getModel().getItems()) {
      String actionId = oldQuickList.getActionId();
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      if (shortcuts.length != 0) {
        String newActionId = newQuickList.getActionId();
        for (Shortcut shortcut : shortcuts) {
          keymap.removeShortcut(actionId, shortcut);
          keymap.addShortcut(newActionId, shortcut);
        }
      }
    }

    myQuickListsModified = true;
  }

  private JPanel createKeymapListPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    JLabel keymapLabel = new JLabel(KeyMapBundle.message("keymaps.border.factory.title"));
    keymapLabel.setLabelFor(myEditor.getComboBox());
    panel.add(keymapLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    panel.add(myEditor.getComboBox(), new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0));

    panel.add(createKeymapButtonsPanel(), new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myEditor.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        currentKeymapChanged();
      }
    });
    panel.add(createKeymapNamePanel(), new GridBagConstraints(3, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));
    return panel;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return new Runnable() {
      @Override
      public void run() {
        showOption(option);
      }
    };
  }

  @Override
  public void processCurrentKeymapChanged(@NotNull QuickList[] ids) {
    myQuickLists = ids;
    currentKeymapChanged();
  }

  private void currentKeymapChanged() {
    myResetToDefault.setEnabled(false);

    Keymap selectedKeymap = myEditor.getModel().getSelected();

    boolean editable = selectedKeymap != null && selectedKeymap.canModify();
    myDeleteButton.setEnabled(editable);
    myCopyButton.setEnabled(selectedKeymap != null);
    myEditor.getComboBox().setEditable(editable);

    if (selectedKeymap == null) {
      myActionsTree.reset(new KeymapImpl(), myQuickLists);
      return;
    }

    Keymap parent = selectedKeymap.getParent();
    if (parent == null || !selectedKeymap.canModify()) {
      myBaseKeymapLabel.setText("");
    }
    else {
      myBaseKeymapLabel.setText(KeyMapBundle.message("based.on.keymap.label", parent.getPresentableName()));
      if (selectedKeymap.canModify() && ((KeymapImpl)selectedKeymap).getOwnActionIds().length > 0) {
        myResetToDefault.setEnabled(true);
      }
    }

    myActionsTree.reset(selectedKeymap, myQuickLists);
  }

  private JPanel createKeymapButtonsPanel() {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    panel.setLayout(new GridBagLayout());
    myCopyButton = new JButton(new AbstractAction(KeyMapBundle.message("copy.keymap.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
            copyKeymap();
      }
    });
    Insets insets = new Insets(2, 2, 2, 2);
    myCopyButton.setMargin(insets);
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0);
    panel.add(myCopyButton, gc);
    myResetToDefault = new JButton(CommonBundle.message("button.reset"));
    myResetToDefault.setMargin(insets);
    panel.add(myResetToDefault, gc);
    myDeleteButton = new JButton(new AbstractAction(KeyMapBundle.message("delete.keymap.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        deleteKeymap();
      }
    });
    myDeleteButton.setMargin(insets);
    gc.weightx = 1;
    panel.add(myDeleteButton, gc);
    IdeFrame ideFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (ideFrame != null && KeyboardSettingsExternalizable.isSupportedKeyboardLayout(ideFrame.getComponent()))
    {
      String displayLanguage = ideFrame.getComponent().getInputContext().getLocale().getDisplayLanguage();
      myNonEnglishKeyboardSupportOption = new JCheckBox(new AbstractAction(displayLanguage + " " + KeyMapBundle.message("use.non.english.keyboard.layout.support")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          KeyboardSettingsExternalizable.getInstance().setNonEnglishKeyboardSupportEnabled(myNonEnglishKeyboardSupportOption.isSelected());
        }
      });
      myNonEnglishKeyboardSupportOption.setSelected(KeyboardSettingsExternalizable.getInstance().isNonEnglishKeyboardSupportEnabled());
      panel.add(myNonEnglishKeyboardSupportOption, gc);
    }

    myResetToDefault.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        resetKeymap();
      }
    });
    return panel;
  }

  private JPanel createKeymapSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    myActionsTree = new ActionsTree();

    panel.add(createToolbarPanel(), BorderLayout.NORTH);
    panel.add(myActionsTree.getComponent(), BorderLayout.CENTER);

    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        editSelection(e);
        return true;
      }
    }.installOn(myActionsTree.getTree());


    myActionsTree.getTree().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
          editSelection(e);
          e.consume();
        }
      }

      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
          editSelection(e);
          e.consume();
        }
      }
    });
    return panel;
  }

  private JPanel createToolbarPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    DefaultActionGroup group = new DefaultActionGroup();
    final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = new TreeExpander() {
      @Override
      public void expandAll() {
        TreeUtil.expandAll(myActionsTree.getTree());
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    };
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
        editSelection(e.getInputEvent());
      }
    });

    panel.add(toolbar, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 0));
    group = new DefaultActionGroup();
    final JComponent searchToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final Alarm alarm = new Alarm();
    myFilterComponent = new FilterComponent("KEYMAP", 5) {
      @Override
      public void filter() {
        alarm.cancelAllRequests();
        alarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (!myFilterComponent.isShowing()) return;
            myTreeExpansionMonitor.freeze();
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
          }
        }, 300);
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 0));

    group.add(new DumbAwareAction(KeyMapBundle.message("filter.shortcut.action.text"),
                                  KeyMapBundle.message("filter.shortcut.action.text"),
                                  AllIcons.Actions.ShortcutFilter) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myFilterComponent.reset();
        if (myPopup == null || myPopup.getContent() == null) {
          myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createFilteringPanel(), null)
            .setRequestFocus(true)
            .setTitle(KeyMapBundle.message("filter.settings.popup.title"))
            .setCancelKeyEnabled(false)
            .setMovable(true)
            .createPopup();
        }
        myPopup.showUnderneathOf(searchToolbar);
      }
    });
    group.add(new DumbAwareAction(KeyMapBundle.message("filter.clear.action.text"),
                                  KeyMapBundle.message("filter.clear.action.text"), AllIcons.Actions.GC) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTreeExpansionMonitor.freeze();
        myActionsTree.filter(null, myQuickLists); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0, 0));
    return panel;
  }

  private JPanel createKeymapNamePanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    myBaseKeymapLabel = new JLabel(KeyMapBundle.message("parent.keymap.label"));
    panel.add(myBaseKeymapLabel,
              new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 16, 0, 0), 0, 0));
    return panel;
  }

  private JPanel createFilteringPanel() {
    //noinspection ConstantConditions
    myActionsTree.reset(myEditor.getModel().getSelected(), myQuickLists);

    final JLabel firstLabel = new JLabel(KeyMapBundle.message("filter.first.stroke.input"));
    final JCheckBox enable2Shortcut = new JCheckBox(KeyMapBundle.message("filter.second.stroke.input"));
    final ShortcutTextField firstShortcut = new ShortcutTextField();
    final ShortcutTextField secondShortcut = new ShortcutTextField();

    enable2Shortcut.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        secondShortcut.setEnabled(enable2Shortcut.isSelected());
        if (enable2Shortcut.isSelected()) {
          secondShortcut.requestFocusInWindow();
        }
      }
    });

    firstShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });

    secondShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });

    IJSwingUtilities.adjustComponentsOnMac(firstLabel, firstShortcut);
    JPanel filterComponent = FormBuilder.createFormBuilder()
      .addLabeledComponent(firstLabel, firstShortcut, true)
      .addComponent(enable2Shortcut)
      .setVerticalGap(0)
      .setHorizontalGap(5)
      .addComponent(secondShortcut)
      .getPanel();

    filterComponent.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));

    enable2Shortcut.setSelected(false);
    secondShortcut.setEnabled(false);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        firstShortcut.requestFocus();
      }
    });
    return filterComponent;
  }

  private void filterTreeByShortcut(final ShortcutTextField firstShortcut,
                                    final JCheckBox enable2Shortcut,
                                    final ShortcutTextField secondShortcut) {
    final KeyStroke keyStroke = firstShortcut.getKeyStroke();
    if (keyStroke != null) {
      myTreeExpansionMonitor.freeze();
      myActionsTree.filterTree(new KeyboardShortcut(keyStroke, enable2Shortcut.isSelected() ? secondShortcut.getKeyStroke() : null),
                               myQuickLists);
      final JTree tree = myActionsTree.getTree();
      TreeUtil.expandAll(tree);
      myTreeExpansionMonitor.restore();
    }
  }

  public void showOption(String option) {
    createFilteringPanel();
    myFilterComponent.setFilter(option);
    myActionsTree.filter(option, myQuickLists);
  }

  private void addKeyboardShortcut(@NotNull String actionId, @Nullable Shortcut shortcut) {
    Keymap keymap = createKeymapCopyIfNeeded();

    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(this, actionId, myQuickLists);
    dialog.setData(keymap, shortcut instanceof KeyboardShortcut ? (KeyboardShortcut)shortcut : null);
    if (!dialog.showAndGet()) {
      return;
    }

    KeyboardShortcut keyboardShortcut = dialog.getKeyboardShortcut();
    if (keyboardShortcut == null) {
      return;
    }

    Map<String, ArrayList<KeyboardShortcut>> conflicts = keymap.getConflicts(actionId, keyboardShortcut);
    if (!conflicts.isEmpty()) {
      int result = Messages.showYesNoCancelDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
        KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
        KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
        Messages.getWarningIcon());

      if (result == Messages.YES) {
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

    repaintLists();
    currentKeymapChanged();
  }

  private void addMouseShortcut(Shortcut shortcut, ShortcutRestrictions restrictions) {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    Keymap keymap = createKeymapCopyIfNeeded();

    MouseShortcut mouseShortcut = shortcut instanceof MouseShortcut ? (MouseShortcut)shortcut : null;

    MouseShortcutDialog dialog = new MouseShortcutDialog(
      this,
      mouseShortcut,
      keymap,
      actionId,
      myActionsTree.getMainGroup(),
      restrictions
    );
    if (!dialog.showAndGet()) {
      return;
    }

    mouseShortcut = dialog.getMouseShortcut();

    if (mouseShortcut == null) {
      return;
    }

    String[] actionIds = keymap.getActionIds(mouseShortcut);
    if (actionIds.length > 1 || (actionIds.length == 1 && !actionId.equals(actionIds[0]))) {
      int result = Messages.showYesNoCancelDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
        KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
        KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
        Messages.getWarningIcon());

      if (result == Messages.YES) {
        for (String id : actionIds) {
          keymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != Messages.NO) {
        return;
      }
    }

    // if shortcut is already registered to this action, just select it in the list

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

    repaintLists();
    currentKeymapChanged();
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
  }

  @NotNull
  private Keymap createKeymapCopyIfNeeded() {
    Keymap keymap = myEditor.getModel().getSelected();
    assert keymap != null;
    if (keymap.canModify()) {
      Keymap mutable = myEditor.getMutable(keymap);
      myActionsTree.setKeymap(mutable);
      return mutable;
    }

    String newKeymapName = KeyMapBundle.message("new.keymap.name", keymap.getPresentableName());
    if (!tryNewKeymapName(newKeymapName)) {
      for (int i = 0; ; i++) {
        newKeymapName = KeyMapBundle.message("new.indexed.keymap.name", keymap.getPresentableName(), i);
        if (tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }

    KeymapImpl newKeymap = ((KeymapImpl)keymap).deriveKeymap();
    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);

    int indexOf = myEditor.getModel().getElementIndex(keymap);
    if (indexOf >= 0) {
      myEditor.getModel().add(indexOf + 1, newKeymap);
    }
    else {
      myEditor.getModel().add(newKeymap);
    }

    myEditor.getModel().setSelectedItem(newKeymap);
    currentKeymapChanged();
    return newKeymap;
  }

  private void copyKeymap() {
    Keymap keymap = myEditor.getModel().getSelected();
    if (keymap == null) {
      return;
    }

    KeymapImpl newKeymap = ((KeymapImpl)keymap).deriveKeymap();

    String newKeymapName = KeyMapBundle.message("new.keymap.name", keymap.getPresentableName());
    if (!tryNewKeymapName(newKeymapName)) {
      for (int i = 0; ; i++) {
        newKeymapName = KeyMapBundle.message("new.indexed.keymap.name", keymap.getPresentableName(), i);
        if (tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }
    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);
    myEditor.getModel().add(newKeymap);
    myEditor.getModel().setSelectedItem(newKeymap);
    myEditor.getComboBox().getEditor().selectAll();
    currentKeymapChanged();
  }

  private boolean tryNewKeymapName(String name) {
    for (int i = 0; i < myEditor.getModel().getSize(); i++) {
      if (name.equals(myEditor.getModel().getElementAt(i).getPresentableName())) {
        return false;
      }
    }

    return true;
  }

  private void deleteKeymap() {
    Keymap keymap = myEditor.getModel().getSelected();
    if (keymap == null || Messages.showYesNoDialog(this, KeyMapBundle.message("delete.keymap.dialog.message"),
                                                   KeyMapBundle.message("delete.keymap.dialog.title"), Messages.getWarningIcon()) != Messages.YES) {
      return;
    }

    myEditor.getModel().remove(keymap);
    currentKeymapChanged();
  }

  private void resetKeymap() {
    Keymap keymap = myEditor.getModel().getSelected();
    if (keymap == null) {
      return;
    }
    ((KeymapImpl)keymap).clearOwnActionsIds();
    currentKeymapChanged();
  }

  @Override
  @NotNull
  public String getId() {
    return "preferences.keymap";
  }

  @Override
  public void reset() {
    if (myNonEnglishKeyboardSupportOption != null) {
      KeyboardSettingsExternalizable.getInstance().setNonEnglishKeyboardSupportEnabled(false);
      myNonEnglishKeyboardSupportOption.setSelected(KeyboardSettingsExternalizable.getInstance().isNonEnglishKeyboardSupportEnabled());
    }

    Keymap selectedKeymap = null;
    List<Keymap> list = getManagerKeymaps();
    for (Keymap keymap : list) {
      if (selectedKeymap == null && keymap == KeymapManagerEx.getInstanceEx().getActiveKeymap()) {
        selectedKeymap = keymap;
      }
    }
    myEditor.reset(list);

    if (myEditor.getModel().isEmpty()) {
      KeymapImpl keymap = new KeymapImpl();
      keymap.setName(KeyMapBundle.message("keymap.no.name"));
      myEditor.getModel().add(keymap);
      selectedKeymap = keymap;
    }

    myEditor.getModel().setSelectedItem(selectedKeymap);

    currentKeymapChanged();
  }

  @Override
  public void apply() throws ConfigurationException {
    myEditor.ensureNonEmptyNames(KeyMapBundle.message("configuration.all.keymaps.should.have.non.empty.names.error.message"));

    ensureUniqueKeymapNames();
    KeymapManagerImpl keymapManager = (KeymapManagerImpl)KeymapManager.getInstance();
    // we must specify the same filter, which was used to get original items
    keymapManager.setKeymaps(myEditor.apply(), myEditor.getModel().getSelected(), KEYMAP_FILTER);
    ActionToolbarImpl.updateAllToolbarsImmediately();
  }

  private void ensureUniqueKeymapNames() throws ConfigurationException {
    Set<String> keymapNames = new THashSet<String>();
    for (Keymap keymap : myEditor.getModel().getItems()) {
      if (!keymapNames.add(keymap.getName())) {
        throw new ConfigurationException(KeyMapBundle.message("configuration.all.keymaps.should.have.unique.names.error.message"));
      }
    }
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myEditor.getModel().getSelected(), KeymapManager.getInstance().getActiveKeymap()) || myEditor.isModified();
  }

  @NotNull
  private static List<Keymap> getManagerKeymaps() {
    return ((KeymapManagerImpl)KeymapManagerEx.getInstanceEx()).getKeymaps(KEYMAP_FILTER);
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
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
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
    Keymap keymap = myEditor.getModel().getSelected();
    return keymap == null ? null : keymap.getShortcuts(actionId);
  }

  private void editSelection(InputEvent e) {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    DefaultActionGroup group = createEditActionGroup(actionId);
    if (e instanceof MouseEvent && ((MouseEvent)e).isPopupTrigger()) {
      ActionManager.getInstance()
        .createActionPopupMenu(ActionPlaces.UNKNOWN, group)
        .getComponent()
        .show(e.getComponent(), ((MouseEvent)e).getX(), ((MouseEvent)e).getY());
    }
    else {
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
  private DefaultActionGroup createEditActionGroup(@NotNull final String actionId) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ShortcutRestrictions restrictions = ActionShortcutRestrictions.getInstance().getForActionId(actionId);
    if (restrictions.allowKeyboardShortcut) {
      group.add(new DumbAwareAction("Add Keyboard Shortcut") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Shortcut firstShortcut = null;
          Keymap keymap = myEditor.getModel().getSelected();
          assert keymap != null;
          for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
            if (shortcut instanceof KeyboardShortcut) {
              firstShortcut = shortcut;
              break;
            }
          }

          addKeyboardShortcut(actionId, firstShortcut);
        }
      });
    }

    if (restrictions.allowMouseShortcut) {
      group.add(new DumbAwareAction("Add Mouse Shortcut") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Shortcut firstMouse = null;
          Keymap keymap = myEditor.getModel().getSelected();
          assert keymap != null;
          for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
            if (shortcut instanceof MouseShortcut) {
              firstMouse = shortcut;
              break;
            }
          }
          addMouseShortcut(firstMouse, restrictions);
        }
      });
    }

    if (Registry.is("actionSystem.enableAbbreviations") && restrictions.allowAbbreviation) {
      group.add(new DumbAwareAction("Add Abbreviation") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String abbr = Messages.showInputDialog("Enter new abbreviation:", "Abbreviation", null);
          if (abbr != null) {
            AbbreviationManager.getInstance().register(abbr, myActionsTree.getSelectedActionId());
            repaintLists();
          }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabledAndVisible(myActionsTree.getSelectedActionId() != null);
        }
      });
    }

    group.addSeparator();

    Keymap keymap = myEditor.getModel().getSelected();
    assert keymap != null;
    for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
      group.add(new DumbAwareAction("Remove " + KeymapUtil.getShortcutText(shortcut)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Keymap keymap = createKeymapCopyIfNeeded();
          keymap.removeShortcut(actionId, shortcut);
          if (StringUtil.startsWithChar(actionId, '$')) {
            keymap.removeShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), shortcut);
          }

          repaintLists();
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
    group.add(new Separator());
    group.add(new DumbAwareAction("Reset Shortcuts") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ((KeymapImpl)createKeymapCopyIfNeeded()).clearOwnActionsId(actionId);
        currentKeymapChanged();
        repaintLists();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(((KeymapImpl)myEditor.getModel().getSelected()).hasOwnActionId(actionId));
      }
    });

    return group;
  }
}
