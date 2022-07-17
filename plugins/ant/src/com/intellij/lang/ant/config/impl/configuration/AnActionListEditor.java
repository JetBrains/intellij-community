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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollingUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnActionListEditor<T> extends JPanel {
  private final Form<T> myForm = new Form<>();
  private final ArrayList<T> myRemoved = new ArrayList<>();
  private final ArrayList<T> myAdded = new ArrayList<>();

  public AnActionListEditor() {
    super(new BorderLayout());
    add(myForm.myWholePanel, BorderLayout.CENTER);
  }

  public void addAddAction(final Factory<? extends T> newItemFactory) {
    ReorderableListController<T>.AddActionDescription description = myForm.getListActionsBuilder().addAddAction(
      AntBundle.message("add.action.name"), newItemFactory, true);
    description.addPostHandler(new ReorderableListController.ActionNotification<>() {
      @Override
      public void afterActionPerformed(T value) {
        myAdded.add(value);
      }
    });
    description.setShowText(true);
  }

  public void addRemoveButtonForAnt(final Condition<? super T> removeCondition, @NlsActions.ActionText String actionName) {
    final ReorderableListController<T>.RemoveActionDescription description = myForm.getListActionsBuilder().addRemoveAction(actionName);
    description.addPostHandler(new ReorderableListController.ActionNotification<>() {
      @Override
      public void afterActionPerformed(List<T> list) {
        for (T item : list) {
          if (myAdded.contains(item)) {
            myAdded.remove(item);
          }
          else {
            myRemoved.add(item);
          }
        }
      }
    });
    description.setEnableCondition(removeCondition);
    description.setConfirmation(list -> {
      if (list.size() == 1) {
        return Messages.showOkCancelDialog(description.getList(),
                                           AntBundle.message("delete.selected.ant.configuration.confirmation.text"),
                                           AntBundle.message("delete.confirmation.dialog.title"),
                                           Messages.getQuestionIcon()) == Messages.OK;
      } else {
        return Messages.showOkCancelDialog(description.getList(),
                                           AntBundle.message("delete.selected.ant.configurations.confirmation.text"),
                                           AntBundle.message("delete.confirmation.dialog.title"),
                                           Messages.getQuestionIcon()) == Messages.OK;
      }
    });

    description.setShowText(true);
  }

  public T getSelectedItem() {
    return myForm.myList.getSelectedValue();
  }

  public void setSelection(T item) {
    myForm.select(item);
  }

  public JList<T> getList() {
    return myForm.myList;
  }

  public ArrayList<T> getAdded() {
    return myAdded;
  }

  public ArrayList<T> getRemoved() {
    return myRemoved;
  }

  public void setItems(Collection<? extends T> items) {
    DefaultListModel<T> model = myForm.getListModel();
    model.removeAllElements();
    for (T item : items) {
      model.addElement(item);
    }
    ScrollingUtil.ensureSelectionExists(getList());
  }

  public void updateItem(T item) {
    myForm.updateItem(item);
  }

  public void actionsBuilt() {
    if (ApplicationManager.getApplication() == null) return;
    myForm.createToolbar();
  }

  private static class Form <T> {
    private JComponent myWholePanel;
    private JPanel myActionsPlace;
    private JList<T> myList;
    private final ReorderableListToolbar<T> myListController;

    Form() {
      myList.setModel(new DefaultListModel<>());
      myListController = new ReorderableListToolbar<>(myList);
    }

    public void createToolbar() {
      myActionsPlace.removeAll();
      myActionsPlace.setLayout(new BorderLayout());
      myActionsPlace.add(myListController.createActionToolbar(true).getComponent(), BorderLayout.CENTER);
    }

    public ReorderableListController<T> getListActionsBuilder() {
      return myListController;
    }

    private DefaultListModel<T> getListModel() {
      return (DefaultListModel<T>)myList.getModel();
    }

    public void select(T item) {
      if (item != null) {
        ScrollingUtil.selectItem(myList, item);
      }
      else {
        ScrollingUtil.ensureSelectionExists(myList);
      }
    }

    public void updateItem(T item) {
      DefaultListModel<T> model = getListModel();
      model.setElementAt(item, model.indexOf(item));
    }
  }
}
