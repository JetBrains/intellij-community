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

import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.ImportSchemeAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * User: anna
 * Date: 13-Apr-2006
 */
public class QuickListsPanel extends JPanel implements SearchableConfigurable, MasterDetails {
  private final DefaultListModel myQuickListsModel = new DefaultListModel();
  private JList myQuickListsList = new JBList(myQuickListsModel);
  private final JPanel myRightPanel = new JPanel(new BorderLayout());
  private int myCurrentIndex = -1;
  private QuickListPanel myQuickListPanel = null;


  private final KeymapPanel myKeymapPanel;
  private JComponent myToolbar;
  private DetailsComponent myDetailsComponent;
  private JBScrollPane myListScrollPane;

  public QuickListsPanel(KeymapPanel panel) {
    super(new BorderLayout());
    myKeymapPanel = panel;
    add(createQuickListsPanel(), BorderLayout.WEST);
    add(myRightPanel, BorderLayout.CENTER);
  }

  public void initUi() {
    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myRightPanel);
  }

  public JComponent getToolbar() {
    return myToolbar;
  }

  public JComponent getMaster() {
    return myListScrollPane;
  }

  public DetailsComponent getDetails() {
    return myDetailsComponent;
  }

  public void reset() {
    myQuickListsModel.removeAllElements();
    QuickList[] allQuickLists = QuickListsManager.getInstance().getAllQuickLists();
    for (QuickList list : allQuickLists) {
      myQuickListsModel.addElement(list);
    }

    if (myQuickListsModel.size() > 0) {
      myQuickListsList.setSelectedIndex(0);
    }
  }

  public boolean isModified() {
    QuickList[] storedLists = QuickListsManager.getInstance().getAllQuickLists();
    QuickList[] modelLists = getCurrentQuickListIds();
    return !Comparing.equal(storedLists, modelLists);
  }

  public void apply() {
    QuickListsManager.getInstance().removeAllQuickLists();
    final QuickList[] currentQuickLists = getCurrentQuickListIds();
    for (QuickList quickList : currentQuickLists) {
      QuickListsManager.getInstance().registerQuickList(quickList);
    }
    QuickListsManager.getInstance().registerActions();
  }

  private JPanel createQuickListsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    myQuickListsList = new JBList(myQuickListsModel);
    myQuickListsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myQuickListsList.setCellRenderer(new MyQuickListCellRenderer());
    myQuickListsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myRightPanel.removeAll();
        final Object selectedValue = myQuickListsList.getSelectedValue();
        if (selectedValue instanceof QuickList){
          final QuickList quickList = (QuickList)selectedValue;
          updateRightPanel(quickList);
          myQuickListsList.repaint();
        }
        else {
          addDescriptionLabel();
        }
        myRightPanel.revalidate();
      }
    });

    addDescriptionLabel();

    myListScrollPane = ScrollPaneFactory.createScrollPane(myQuickListsList);
    myListScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    final Dimension dimension = new Dimension(120, -1);
    myListScrollPane.setPreferredSize(dimension);
    myListScrollPane.setMinimumSize(dimension);
    panel.add(myListScrollPane, BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup();
    ReorderableListController<QuickList> controller = ReorderableListController.create(myQuickListsList, group);
    final ReorderableListController<QuickList>.AddActionDescription addActionDescription =
      controller.addAddAction(KeyMapBundle.message("add.keymap.label"), new Factory<QuickList>() {
        public QuickList create() {
          return new QuickList(createUniqueName(), "", ArrayUtil.EMPTY_STRING_ARRAY, false);
        }
      }, true);
    addActionDescription.addPostHandler(new ReorderableListController.ActionNotification<QuickList>() {
      public void afterActionPerformed(final QuickList change) {
        myKeymapPanel.processCurrentKeymapChanged();
      }
    });
    final ReorderableListController<QuickList>.RemoveActionDescription removeActionDescription =
      controller.addRemoveAction(KeyMapBundle.message("remove.keymap.label"));
    removeActionDescription.addPostHandler(new ReorderableListController.ActionNotification<List<QuickList>>() {
      public void afterActionPerformed(final List<QuickList> change) {
        myQuickListsList.repaint();
        myKeymapPanel.processCurrentKeymapChanged();
      }
    });

    SchemesManager<QuickList,QuickList> schemesManager = QuickListsManager.getInstance().getSchemesManager();
    if (schemesManager.isExportAvailable()) {
      group.add(new ExportSchemeAction<QuickList, QuickList>(schemesManager){
        protected QuickList getSelectedScheme() {
          return (QuickList)myQuickListsList.getSelectedValue();
        }
      });
    }

    if (schemesManager.isImportAvailable()) {
      group.add(new ImportSchemeAction<QuickList,QuickList>(QuickListsManager.getInstance().getSchemesManager()){
        protected Collection<QuickList> collectCurrentSchemes() {
          return collectElements();
        }

        protected Component getPanel() {
          return myQuickListsList;
        }

        protected void importScheme(final QuickList scheme) {
          myQuickListsModel.addElement(scheme);
          myQuickListsList.clearSelection();
          ListScrollingUtil.selectItem(myQuickListsList, scheme);

        }
      });
    }

    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    panel.add(myToolbar, BorderLayout.NORTH);
    return panel;
  }

  private void addDescriptionLabel() {
    final JLabel descLabel =new JLabel("<html>Quick Lists allow you to define commonly used groups of actions (for example, refactoring or VCS actions)" +
                 " and to assign keyboard shortcuts to such groups.</html>");
    descLabel.setBorder(new EmptyBorder(0, 25, 0, 25));
    myRightPanel.add(descLabel, BorderLayout.CENTER);
  }


  private Collection<QuickList> collectElements() {
    HashSet<QuickList> result = new HashSet<QuickList>();
    for (int i = 0; i < myQuickListsModel.getSize(); i++) {
      result.add((QuickList)myQuickListsModel.getElementAt(i));
    }
    return result;
  }

  private String createUniqueName() {
    String str = KeyMapBundle.message("unnamed.list.display.name");
    final ArrayList<String> names = new ArrayList<String>();
    for (int i = 0; i < myQuickListsModel.getSize(); i++) {
      names.add(((QuickList)myQuickListsModel.getElementAt(i)).getDisplayName());
    }
    if (!names.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!names.contains(str + i)) return str + i;
      i++;
    }
  }

  private void updateRightPanel(final QuickList quickList) {
    final int index = myQuickListsList.getSelectedIndex();
    if (myQuickListPanel != null && myCurrentIndex > -1 && myCurrentIndex < myQuickListsModel.getSize()){
      updateList(myCurrentIndex);
      myKeymapPanel.processCurrentKeymapChanged();
    }
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    myQuickListPanel = new QuickListPanel(quickList, getCurrentQuickListIds(), project);
    final DocumentAdapter documentAdapter = new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateList(index);
      }
    };
    myQuickListPanel.addNameListener(documentAdapter);
    myQuickListPanel.addDescriptionListener(documentAdapter);
    myRightPanel.add(myQuickListPanel.getPanel(), BorderLayout.CENTER);
    myCurrentIndex = index;

    if (myDetailsComponent != null) {
      myDetailsComponent.setText(quickList.getDisplayName());
    }
  }

  private void updateList(int index)  {
    if (myQuickListPanel == null) return;
    QuickList oldQuickList = (QuickList)myQuickListsModel.getElementAt(index);

    QuickList newQuickList = createNewQuickListAt();

    if (oldQuickList != null) {
      newQuickList.getExternalInfo().copy(oldQuickList.getExternalInfo());
    }

    myQuickListsModel.setElementAt(newQuickList, index);

    if (oldQuickList != null && !newQuickList.getName().equals(oldQuickList.getName())) {
      myKeymapPanel.quickListRenamed(oldQuickList, newQuickList);
      if (myDetailsComponent != null) {
        myDetailsComponent.setText(newQuickList.getDisplayName());
      }
    }
  }

  private QuickList createNewQuickListAt() {
    ListModel model = myQuickListPanel.getActionsList().getModel();
    int size = model.getSize();
    String[] ids = new String[size];
    for (int i = 0; i < size; i++) {
      ids[i] = (String)model.getElementAt(i);
    }
    QuickList newQuickList = new QuickList(myQuickListPanel.getDisplayName(), myQuickListPanel.getDescription(), ids, false);
    return newQuickList;
  }


  public QuickList[] getCurrentQuickListIds() {
    if (myCurrentIndex > - 1 && myQuickListsModel.getSize() > myCurrentIndex){
      updateList(myCurrentIndex);
    }
    int size = myQuickListsModel.size();
    QuickList[] lists = new QuickList[size];
    for (int i = 0; i < lists.length; i++) {
      lists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }
    return lists;
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  private static class MyQuickListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      QuickList quickList = (QuickList)value;
      setText(quickList.getDisplayName());
      return this;
    }
  }

  @Nls
  public String getDisplayName() {
    return "Quick Lists";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.idesettings.quicklists";
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
  }
}
