/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: 13-Apr-2006
 */
public class QuickListsPanel extends JPanel implements SearchableConfigurable, Configurable.NoScroll {
  private final DefaultListModel myQuickListsModel = new DefaultListModel();
  private JBList myQuickListsList = new JBList(myQuickListsModel);
  private final JPanel myRightPanel = new JPanel(new BorderLayout());
  private int myCurrentIndex = -1;
  private QuickListPanel myQuickListPanel = null;
  private final KeymapListener myKeymapListener;

  public QuickListsPanel() {
    super(new BorderLayout());
    myKeymapListener = ApplicationManager.getApplication().getMessageBus().syncPublisher(KeymapListener.CHANGE_TOPIC);
    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(createQuickListsPanel());
    splitter.setSecondComponent(myRightPanel);
    add(splitter, BorderLayout.CENTER);
  }

  @Override
  public void reset() {
    myQuickListsModel.removeAllElements();
    for (QuickList list : QuickListsManager.getInstance().getAllQuickLists()) {
      myQuickListsModel.addElement(list);
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myQuickListsModel.isEmpty()) {
          myQuickListsList.setSelectedIndex(0);
        }
      }
    });
  }

  @Override
  public boolean isModified() {
    QuickList[] storedLists = QuickListsManager.getInstance().getAllQuickLists();
    QuickList[] modelLists = getCurrentQuickListIds();
    return !Comparing.equal(storedLists, modelLists);
  }

  @Override
  public void apply() {
    QuickListsManager.getInstance().setQuickLists(getCurrentQuickListIds());
  }

  private JPanel createQuickListsPanel() {
    myQuickListsList = new JBList(myQuickListsModel);
    myQuickListsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myQuickListsList.setCellRenderer(new MyQuickListCellRenderer());
    myQuickListsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myRightPanel.removeAll();
        final Object selectedValue = myQuickListsList.getSelectedValue();
        if (selectedValue instanceof QuickList) {
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

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myQuickListsList).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        QuickList quickList = new QuickList(createUniqueName(), "", ArrayUtil.EMPTY_STRING_ARRAY, false);
        myQuickListsModel.addElement(quickList);
        myQuickListsList.clearSelection();
        ListScrollingUtil.selectItem(myQuickListsList, quickList);
        myKeymapListener.processCurrentKeymapChanged(getCurrentQuickListIds());
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.removeSelectedItems(myQuickListsList);
        myQuickListsList.repaint();
        myKeymapListener.processCurrentKeymapChanged(getCurrentQuickListIds());
      }
    }).disableUpDownActions();
    return toolbarDecorator.createPanel();
  }

  private void addDescriptionLabel() {
    final JLabel descLabel =
      new JLabel("<html>Quick Lists allow you to define commonly used groups of actions (for example, refactoring or VCS actions)" +
                 " and to assign keyboard shortcuts to such groups.</html>");
    descLabel.setBorder(new EmptyBorder(0, 25, 0, 25));
    myRightPanel.add(descLabel, BorderLayout.CENTER);
  }

  private String createUniqueName() {
    String str = KeyMapBundle.message("unnamed.list.display.name");
    final ArrayList<String> names = new ArrayList<String>();
    for (int i = 0; i < myQuickListsModel.getSize(); i++) {
      names.add(((QuickList)myQuickListsModel.getElementAt(i)).getName());
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
    if (myQuickListPanel != null && myCurrentIndex > -1 && myCurrentIndex < myQuickListsModel.getSize()) {
      updateList(myCurrentIndex);
      myKeymapListener.processCurrentKeymapChanged(getCurrentQuickListIds());
    }
    myQuickListPanel = new QuickListPanel(quickList, getCurrentQuickListIds());
    final DocumentAdapter documentAdapter = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateList(index);
      }
    };
    myQuickListPanel.addNameListener(documentAdapter);
    myQuickListPanel.addDescriptionListener(documentAdapter);
    myRightPanel.add(myQuickListPanel.getPanel(), BorderLayout.CENTER);
    myCurrentIndex = index;
  }

  private void updateList(int index) {
    if (myQuickListPanel == null) return;
    QuickList oldQuickList = (QuickList)myQuickListsModel.getElementAt(index);

    QuickList newQuickList = createNewQuickListAt();

    if (oldQuickList != null) {
      newQuickList.getExternalInfo().copy(oldQuickList.getExternalInfo());
    }

    myQuickListsModel.setElementAt(newQuickList, index);

    if (oldQuickList != null && !newQuickList.getName().equals(oldQuickList.getName())) {
      myKeymapListener.quickListRenamed(oldQuickList, newQuickList);
    }
  }

  private QuickList createNewQuickListAt() {
    ListModel model = myQuickListPanel.getActionsList().getModel();
    int size = model.getSize();
    String[] ids = new String[size];
    for (int i = 0; i < size; i++) {
      ids[i] = (String)model.getElementAt(i);
    }
    return new QuickList(myQuickListPanel.getDisplayName(), myQuickListPanel.getDescription(), ids, false);
  }

  @NotNull
  private QuickList[] getCurrentQuickListIds() {
    if (myCurrentIndex > -1 && myQuickListsModel.getSize() > myCurrentIndex) {
      updateList(myCurrentIndex);
    }
    int size = myQuickListsModel.size();
    QuickList[] lists = new QuickList[size];
    for (int i = 0; i < lists.length; i++) {
      lists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }
    return lists;
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  private static class MyQuickListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setBackground(UIUtil.getListBackground(selected));
      QuickList quickList = (QuickList)value;
      append(quickList.getName());
    }
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Quick Lists";
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "reference.idesettings.quicklists";
  }

  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public void disposeUIResources() {
  }
}
