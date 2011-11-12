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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.CommonBundle;
import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedComboBoxEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class KeymapPanel extends JPanel implements SearchableConfigurable {
  private JComboBox myKeymapList;

  private final DefaultComboBoxModel myKeymapListModel = new DefaultComboBoxModel();

  private KeymapImpl mySelectedKeymap;

  private JButton myCopyButton;
  private JButton myDeleteButton;
  private JButton myResetToDefault;

  private JLabel myBaseKeymapLabel;

  private ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private JBPopup myPopup = null;
  private TreeExpansionMonitor myTreeExpansionMonitor;

  private boolean myQuickListsModified = false;
  private JButton myExportButton;
  private QuickListsPanel myQuickListsPanel;

  public KeymapPanel() {
    setLayout(new BorderLayout());
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.add(createKeymapListPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);
    add(keymapPanel, BorderLayout.CENTER);
    addPropertyChangeListener(new PropertyChangeListener(){
      public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals("ancestor") && evt.getNewValue() != null && evt.getOldValue() == null && myQuickListsModified) {
          processCurrentKeymapChanged();
          myQuickListsModified = false;
        }

      }
    });
  }

  public void setQuickListsPanel(final QuickListsPanel quickListsPanel) {
    myQuickListsPanel = quickListsPanel;
  }

  public void quickListRenamed(final QuickList oldQuickList, final QuickList newQuickList){

    for (Keymap keymap : getAllKeymaps()) {
      KeymapImpl impl = (KeymapImpl)keymap;

      String actionId = oldQuickList.getActionId();
      String newActionId = newQuickList.getActionId();

      Shortcut[] shortcuts = impl.getShortcuts(actionId);

      if (shortcuts != null) {
        for (Shortcut shortcut : shortcuts) {
          impl.removeShortcut(actionId,  shortcut);          
          impl.addShortcut(newActionId, shortcut);
        }
      }
    }

    myQuickListsModified = true;
  }

  private JPanel createKeymapListPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    myKeymapList = new ComboBox(myKeymapListModel);
    myKeymapList.setEditor(new MyEditor());
    myKeymapList.setRenderer(new MyKeymapRenderer(myKeymapList.getRenderer()));
    JLabel keymapLabel = new JLabel(KeyMapBundle.message("keymaps.border.factory.title"));
    keymapLabel.setLabelFor(myKeymapList);
    panel.add(keymapLabel, new GridBagConstraints(0,0, 1, 1, 0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    panel.add(myKeymapList, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,4,0,0),0,0));

    panel.add(createKeymapButtonsPanel(), new GridBagConstraints(2,0,1,1,0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0));
    myKeymapList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myKeymapListModel.getSelectedItem() != mySelectedKeymap) processCurrentKeymapChanged();
      }
    });
    panel.add(createKeymapNamePanel(), new GridBagConstraints(3,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,10,0,0),0,0));
    return panel;
  }

  public Runnable enableSearch(final String option) {
    return new Runnable(){
      public void run() {
        showOption(option);
      }
    };
  }

  void processCurrentKeymapChanged() {
    myCopyButton.setEnabled(false);
    myDeleteButton.setEnabled(false);
    myResetToDefault.setEnabled(false);

    if (myExportButton != null) {
      myExportButton.setEnabled(false);
    }

    KeymapImpl selectedKeymap = getSelectedKeymap();
    mySelectedKeymap = selectedKeymap;
    if (selectedKeymap == null) {
      myActionsTree.reset(new KeymapImpl(), getCurrentQuickListIds());
      return;
    }
    myKeymapList.setEditable(mySelectedKeymap.canModify());

    myCopyButton.setEnabled(true);
    myBaseKeymapLabel.setText("");
    Keymap parent = mySelectedKeymap.getParent();
    if (parent != null && mySelectedKeymap.canModify()) {
      myBaseKeymapLabel.setText(KeyMapBundle.message("based.on.keymap.label", parent.getPresentableName()));
      if (mySelectedKeymap.canModify() && mySelectedKeymap.getOwnActionIds().length > 0){
        myResetToDefault.setEnabled(true);
      }
    }
    if(mySelectedKeymap.canModify()) {
      myDeleteButton.setEnabled(true);

      if (!getSchemesManager().isShared(mySelectedKeymap)) {
        if (myExportButton != null) {
          myExportButton.setEnabled(true);
        }
      }
    }

    myActionsTree.reset(mySelectedKeymap, getCurrentQuickListIds());
  }

  private KeymapImpl getSelectedKeymap() {
    return (KeymapImpl)myKeymapList.getSelectedItem();
  }

  List<Keymap> getAllKeymaps() {
    ListModel model = myKeymapList.getModel();
    List<Keymap> result = new ArrayList<Keymap>();
    for (int i = 0; i < model.getSize(); i++) {
      result.add((Keymap)model.getElementAt(i));
    }
    return result;
  }


  private JPanel createKeymapButtonsPanel() {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    panel.setLayout(new GridBagLayout());
    myCopyButton = new JButton(KeyMapBundle.message("copy.keymap.button"));
    Insets insets = new Insets(2, 2, 2, 2);
    myCopyButton.setMargin(insets);
    final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0);
    panel.add(myCopyButton, gc);
    myResetToDefault = new JButton(CommonBundle.message("button.reset"));
    myResetToDefault.setMargin(insets);
    panel.add(myResetToDefault, gc);
    myDeleteButton = new JButton(KeyMapBundle.message("delete.keymap.button"));
    myDeleteButton.setMargin(insets);
    gc.weightx = 1;
    panel.add(myDeleteButton, gc);

    final SchemesManager<Keymap, KeymapImpl> schemesManager = getSchemesManager();
    if (schemesManager.isExportAvailable()) {
      myExportButton = new JButton("Share...");
      myExportButton.setMnemonic('S');
      myExportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          KeymapImpl selected = getSelectedKeymap();
          ExportSchemeAction.doExport(selected, schemesManager);
        }
      });
      myExportButton.setMargin(insets);


      panel.add(myExportButton, gc);


    }

    if (schemesManager.isImportAvailable()) {
      JButton importButton = new JButton("Import Shared...");
      importButton.setMnemonic('I');
      importButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          SchemesToImportPopup<Keymap, KeymapImpl> popup = new SchemesToImportPopup<Keymap, KeymapImpl>(panel){
            protected void onSchemeSelected(final KeymapImpl scheme) {
              if (scheme != null) {
                scheme.setCanModify(true);
                myKeymapListModel.addElement(scheme);
                myKeymapList.setSelectedItem(scheme);
                processCurrentKeymapChanged();

              }

            }
          };
          popup.show(schemesManager, collectKeymaps(myKeymapListModel));

        }
      });

      importButton.setMargin(insets);
      panel.add(importButton,gc);
      
    }


    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          copyKeymap();
        }
      }
    );

    myResetToDefault.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        resetKeymap();
      }

    });

    myDeleteButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          deleteKeymap();
        }
      }
    );

    return panel;
  }

  private static Collection<Keymap> collectKeymaps(final DefaultComboBoxModel list) {
    HashSet<Keymap> names = new HashSet<Keymap>();
    for (int i = 0; i < list.getSize(); i++) {
      names.add((Keymap)list.getElementAt(i));

    }
    return names;
  }

  private static SchemesManager<Keymap,KeymapImpl> getSchemesManager() {
    return ((KeymapManagerEx)KeymapManager.getInstance()).getSchemesManager();
  }

  private JPanel createKeymapSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    myActionsTree = new ActionsTree();

    panel.add(createToolbarPanel(), BorderLayout.NORTH);
    panel.add(myActionsTree.getComponent(), BorderLayout.CENTER);

    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());

    myActionsTree.getTree().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 || e.isPopupTrigger()) {
          editSelection(e);
          e.consume();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
          editSelection(e);
          e.consume();
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
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
      public void expandAll() {
        TreeUtil.expandAll(myActionsTree.getTree());
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    group.add(commonActionsManager.createExpandAllAction(treeExpander, myActionsTree.getTree()));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander, myActionsTree.getTree()));
    
    group.add(new AnAction("Edit Shortcut", "Edit Shortcut", IconLoader.getIcon("/actions/properties.png")) {
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), myActionsTree.getTree());
      }

      @Override
      public void update(AnActionEvent e) {
        final String actionId = myActionsTree.getSelectedActionId();
        e.getPresentation().setEnabled(actionId != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        editSelection(e.getInputEvent());
      }
    });

    panel.add(toolbar, new GridBagConstraints(0,0,1,1,1,0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8,0,0,0), 0,0));
    group = new DefaultActionGroup();
    final JComponent searchToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final Alarm alarm = new Alarm();
    myFilterComponent = new FilterComponent("KEYMAP", 5){
      public void filter() {
        alarm.cancelAllRequests();
        alarm.addRequest(new Runnable() {
          public void run() {
            if (!myFilterComponent.isShowing()) return;
            if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
            final String filter = getFilter();
            myActionsTree.filter(filter, getCurrentQuickListIds());
            final JTree tree = myActionsTree.getTree();
            TreeUtil.expandAll(tree);
            if (filter == null || filter.length() == 0){
              TreeUtil.collapseAll(tree, 0);
              myTreeExpansionMonitor.restore();
            }
          }
        }, 300);
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0,0));

    group.add(new AnAction(KeyMapBundle.message("filter.shortcut.action.text"),
                           KeyMapBundle.message("filter.shortcut.action.text"),
                           IconLoader.getIcon("/ant/shortcutFilter.png")) {
      public void actionPerformed(AnActionEvent e) {
        myFilterComponent.reset();
        if (myPopup == null || myPopup.getContent() == null){
          myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createFilteringPanel(), null)
            .setRequestFocus(true)
            .setTitle(KeyMapBundle.message("filter.settings.popup.title"))
            .setMovable(true)
            .createPopup();
        }
        myPopup.showUnderneathOf(searchToolbar);
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.clear.action.text"), IconLoader.getIcon("/actions/gc.png")) {
      public void actionPerformed(AnActionEvent e) {
        myActionsTree.filter(null, getCurrentQuickListIds()); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0,0));
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
    myActionsTree.reset(getSelectedKeymap(), getCurrentQuickListIds());
    JPanel filterComponent = new JPanel(new GridBagLayout());
    filterComponent.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    final ShortcutTextField firstShortcut = new ShortcutTextField();
    final ShortcutTextField secondShortcut = new ShortcutTextField();
    final JCheckBox enable2Shortcut = new JCheckBox(KeyMapBundle.message("filter.enable.second.stroke.checkbox"));
    firstShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });
    secondShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });
    final JLabel firstLabel = new JLabel(KeyMapBundle.message("filter.first.stroke.input"));
    final JLabel secondLabel = new JLabel(KeyMapBundle.message("filter.second.stroke.input"));
    filterComponent.add(firstLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(5,2,0,0),0,0));
    filterComponent.add(firstShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    enable2Shortcut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        secondShortcut.setEnabled(enable2Shortcut.isSelected());
        secondLabel.setEnabled(enable2Shortcut.isSelected());
        if (enable2Shortcut.isSelected()){
          secondShortcut.requestFocusInWindow();
        }
      }
    });
    filterComponent.add(enable2Shortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));
    filterComponent.add(secondLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    filterComponent.add(secondShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    enable2Shortcut.setSelected(false);
    secondLabel.setEnabled(false);
    secondShortcut.setEnabled(false);
    SwingUtilities.invokeLater(new Runnable() {
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
    if (keyStroke != null){
      if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
      myActionsTree.filterTree(new KeyboardShortcut(keyStroke, enable2Shortcut.isSelected() ? secondShortcut.getKeyStroke() : null),
                               getCurrentQuickListIds());
      final JTree tree = myActionsTree.getTree();
      TreeUtil.expandAll(tree);
    }
  }

  public void showOption(String option){
    createFilteringPanel();
    myFilterComponent.setFilter(option);
    myActionsTree.filter(option, getCurrentQuickListIds());
  }

  private void addKeyboardShortcut(Shortcut shortcut) {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    if (!createKeymapCopyIfNeeded()) return;

    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(this, actionId, getCurrentQuickListIds());


    KeyboardShortcut selectedKeyboardShortcut = shortcut instanceof KeyboardShortcut ? (KeyboardShortcut)shortcut : null;

    dialog.setData(mySelectedKeymap, selectedKeyboardShortcut);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    KeyboardShortcut keyboardShortcut = dialog.getKeyboardShortcut();

    if (keyboardShortcut == null) {
      return;
    }

    HashMap<String, ArrayList<KeyboardShortcut>> conflicts = mySelectedKeymap.getConflicts(actionId, keyboardShortcut);
    if(conflicts.size() > 0) {
      int result = Messages.showYesNoCancelDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
        Messages.getWarningIcon());

      if(result == 0) {
        for (String id : conflicts.keySet()) {
          for (KeyboardShortcut s : conflicts.get(id)) {
            mySelectedKeymap.removeShortcut(id, s);
          }
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is already registered to this action, just select it in the list
    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (Shortcut s : shortcuts) {
      if (s.equals(keyboardShortcut)) {
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, keyboardShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), keyboardShortcut);
    }

    repaintLists();
    processCurrentKeymapChanged();
  }

  private QuickList[] getCurrentQuickListIds() {
    if (myQuickListsPanel != null) {
      return myQuickListsPanel.getCurrentQuickListIds();
    }
    else {
      return QuickListsManager.getInstance().getAllQuickLists();

    }
  }

  private void addMouseShortcut(Shortcut shortcut){
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    if (!createKeymapCopyIfNeeded()) return;

    MouseShortcut mouseShortcut = shortcut instanceof MouseShortcut ? (MouseShortcut)shortcut : null;

    MouseShortcutDialog dialog = new MouseShortcutDialog(
      this,
      mouseShortcut,
      mySelectedKeymap,
      actionId,
      myActionsTree.getMainGroup()
    );
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    mouseShortcut = dialog.getMouseShortcut();

    if (mouseShortcut == null){
      return;
    }

    String[] actionIds = mySelectedKeymap.getActionIds(mouseShortcut);
    if(actionIds.length > 1 || (actionIds.length == 1 && !actionId.equals(actionIds[0]))) {
      int result = Messages.showYesNoCancelDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
        Messages.getWarningIcon());

      if(result == 0) {
        for (String id : actionIds) {
          mySelectedKeymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is aleady registered to this action, just select it in the list

    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (Shortcut shortcut1 : shortcuts) {
      if (shortcut1.equals(mouseShortcut)) {
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, mouseShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), mouseShortcut);
    }

    repaintLists();
    processCurrentKeymapChanged();
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
  }

  private boolean createKeymapCopyIfNeeded() {
    if (mySelectedKeymap.canModify()) return true;

    final KeymapImpl selectedKeymap = getSelectedKeymap();
    if(selectedKeymap == null) {
      return false;
    }

    KeymapImpl newKeymap = selectedKeymap.deriveKeymap();

    String newKeymapName = KeyMapBundle.message("new.keymap.name", selectedKeymap.getPresentableName());
    if(!tryNewKeymapName(newKeymapName)) {
      for(int i=0; ; i++) {
        newKeymapName = KeyMapBundle.message("new.indexed.keymap.name", selectedKeymap.getPresentableName(), i);
        if(tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }

    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);

    final int indexOf = myKeymapListModel.getIndexOf(selectedKeymap);
    if (indexOf >= 0) {
      myKeymapListModel.insertElementAt(newKeymap, indexOf + 1);
    } else {
      myKeymapListModel.addElement(newKeymap);
    }

    myKeymapList.setSelectedItem(newKeymap);
    processCurrentKeymapChanged();

    return true;
  }

  private void removeShortcut(Shortcut shortcut) {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    if (!createKeymapCopyIfNeeded()) return;

    if(shortcut == null) return;

    mySelectedKeymap.removeShortcut(actionId, shortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.removeShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), shortcut);
    }

    repaintLists();
    processCurrentKeymapChanged();
  }

  private void copyKeymap() {
    KeymapImpl keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    KeymapImpl newKeymap = keymap.deriveKeymap();

    String newKeymapName = KeyMapBundle.message("new.keymap.name", keymap.getPresentableName());
    if(!tryNewKeymapName(newKeymapName)) {
      for(int i=0; ; i++) {
        newKeymapName = KeyMapBundle.message("new.indexed.keymap.name", keymap.getPresentableName(), i);
        if(tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }
    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);
    myKeymapListModel.addElement(newKeymap);
    myKeymapList.setSelectedItem(newKeymap);
    myKeymapList.getEditor().selectAll();
    processCurrentKeymapChanged();
  }

  private boolean tryNewKeymapName(String name) {
    for(int i=0; i<myKeymapListModel.getSize(); i++) {
      Keymap k = (Keymap)myKeymapListModel.getElementAt(i);
      if(name.equals(k.getPresentableName())) {
        return false;
      }
    }

    return true;
  }

  private void deleteKeymap() {
    Keymap keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    int result = Messages.showYesNoDialog(this, KeyMapBundle.message("delete.keymap.dialog.message"),
                                          KeyMapBundle.message("delete.keymap.dialog.title"), Messages.getWarningIcon());
    if (result != 0) {
      return;
    }
    myKeymapListModel.removeElement(myKeymapList.getSelectedItem());
    processCurrentKeymapChanged();
  }

  private void resetKeymap() {
    Keymap keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    ((KeymapImpl)keymap).clearOwnActionsIds();
    processCurrentKeymapChanged();
  }

  @NotNull
  public String getId() {
    return "preferences.keymap";
  }

  private static final class MyKeymapRenderer extends ListCellRendererWrapper<Keymap> {
    public MyKeymapRenderer(final ListCellRenderer listCellRenderer) {
      super(listCellRenderer);
    }

    @Override
    public void customize(JList list, Keymap keymap, int index, boolean selected, boolean hasFocus) {
      if (keymap != null) {
        String name = keymap.getPresentableName();
        if (name == null) {
          name = KeyMapBundle.message("keymap.noname.presentable.name");
        }
        setText(name);
      }
    }
  }

  public void reset() {
    myKeymapListModel.removeAllElements();
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    Keymap[] keymaps = keymapManager.getAllKeymaps();
    for (Keymap keymap1 : keymaps) {
      KeymapImpl keymap = (KeymapImpl)keymap1;
      if (keymap.canModify()) {
        keymap = keymap.copy(true);
      }

      myKeymapListModel.addElement(keymap);
      if (Comparing.equal(keymapManager.getActiveKeymap(), keymap1)) {
        mySelectedKeymap = keymap;
      }
    }

    if(myKeymapListModel.getSize() == 0) {
      KeymapImpl keymap = new KeymapImpl();
      keymap.setName(KeyMapBundle.message("keymap.no.name"));
      myKeymapListModel.addElement(keymap);
    }

    myKeymapList.setSelectedItem(mySelectedKeymap);
  }

  public void apply() throws ConfigurationException{
    ensureNonEmptyKeymapNames();
    ensureUniqueKeymapNames();
    final KeymapManagerImpl keymapManager = (KeymapManagerImpl)KeymapManager.getInstance();
    keymapManager.removeAllKeymapsExceptUnmodifiable();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.getElementAt(i);
      if(modelKeymap.canModify()) {
        final KeymapImpl keymapToAdd = ((KeymapImpl)modelKeymap).copy(true);
        keymapManager.addKeymap(keymapToAdd);
      }
    }
    keymapManager.setActiveKeymap(mySelectedKeymap);
  }

  private void ensureNonEmptyKeymapNames() throws ConfigurationException {
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.getElementAt(i);
      if (StringUtil.isEmptyOrSpaces(modelKeymap.getName())) {
        throw new ConfigurationException(KeyMapBundle.message("configuration.all.keymaps.should.have.non.empty.names.error.message"));
      }
    }
  }

  private void ensureUniqueKeymapNames() throws ConfigurationException {
    final Set<String> keymapNames = new HashSet<String>();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.getElementAt(i);
      String name = modelKeymap.getName();
      if (keymapNames.contains(name)) {
        throw new ConfigurationException(KeyMapBundle.message("configuration.all.keymaps.should.have.unique.names.error.message"));
      }
      keymapNames.add(name);
    }
  }

  public boolean isModified() {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    if (!Comparing.equal(mySelectedKeymap, keymapManager.getActiveKeymap())) {
      return true;
    }
    Keymap[] managerKeymaps = keymapManager.getAllKeymaps();
    Keymap[] panelKeymaps = new Keymap[myKeymapListModel.getSize()];
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      panelKeymaps[i] = (Keymap)myKeymapListModel.getElementAt(i);
    }
    return !Comparing.equal(managerKeymaps, panelKeymaps);
  }

  public void selectAction(String actionId) {
    myActionsTree.selectAction(actionId);
  }

  private static class MyEditor extends FixedComboBoxEditor {
    private KeymapImpl myKeymap = null;

    public MyEditor() {
      getField().getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          if (myKeymap != null && myKeymap.canModify()){
            myKeymap.setName(getField().getText());
          }
        }
      });
    }

    public void setItem(Object anObject) {
      if (anObject instanceof KeymapImpl){
        myKeymap = (KeymapImpl)anObject;
        getField().setText(myKeymap.getPresentableName());
      }
    }

    public Object getItem() {
      return myKeymap;
    }
  }

  @Nls
  public String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
    if (myFilterComponent != null) {
      myFilterComponent.dispose();
    }
  }

  private void editSelection(InputEvent e) {
    final String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) return;

    DefaultActionGroup group = new DefaultActionGroup();

    final Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    
    group.add(new AnAction("Add Keyboard Shortcut") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Shortcut firstKeyboard = null;
        for (Shortcut shortcut : shortcuts) {
          if (shortcut instanceof KeyboardShortcut) {
            firstKeyboard = shortcut;
            break;
          }
        }

        addKeyboardShortcut(firstKeyboard);
      }
    });
    
    group.add(new AnAction("Add Mouse Shortcut") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Shortcut firstMouse = null;
        for (Shortcut shortcut : shortcuts) {
          if (shortcut instanceof MouseShortcut) {
            firstMouse = shortcut;
            break;
          }
        }
        addMouseShortcut(firstMouse);
      }
    });

    group.addSeparator();

    for (final Shortcut shortcut : shortcuts) {
      group.add(new AnAction("Remove " + KeymapUtil.getShortcutText(shortcut)) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          removeShortcut(shortcut);
        }
      });
    }

    if (e instanceof MouseEvent && ((MouseEvent)e).isPopupTrigger()) {
      final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
      popupMenu.getComponent().show(e.getComponent(), ((MouseEvent)e).getX(), ((MouseEvent)e).getY());
    }
    else {
      final DataContext dataContext = DataManager.getInstance().getDataContext(this);
      final ListPopup popup = JBPopupFactory.getInstance()
        .createActionGroupPopup("Edit Shortcuts",
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
}
