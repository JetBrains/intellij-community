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
package com.intellij.util.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.newArrayList;


public abstract class ListItemsDialogWrapper extends DialogWrapper {
  protected final JPanel myPanel;
  protected final JList<String> myList = new JBList<>(new DefaultListModel());
  protected List<String> myData;

  public ListItemsDialogWrapper(String title) {
    super(true);
    myPanel = ToolbarDecorator.createDecorator(myList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final String elementName = createAddItemDialog();
          if (elementName != null) {
            while (myData.contains(elementName)) {
              myData.remove(elementName);
            }
            myData.add(elementName);
            updateData();
            myList.setSelectedIndex(myData.size() - 1);
          }
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int selectedIndex = myList.getSelectedIndex();
          if (selectedIndex >= 0) {
            myData.remove(selectedIndex);
            updateData();
            if (selectedIndex >= myData.size()) {
              selectedIndex -= 1;
            }
            if (selectedIndex >= 0) {
              myList.setSelectedIndex(selectedIndex);
            }
          }
        }
      }).disableUpDownActions().createPanel();
    setTitle(title);
    init();
  }

  protected abstract String createAddItemDialog();

  public void setData(List<String> data) {
    myData = newArrayList(data);
    updateData();
    if (!myData.isEmpty()) {
      myList.setSelectedIndex(0);
    }
  }

  protected void updateData() {
    final DefaultListModel<String> model = ((DefaultListModel<String>)myList.getModel());
    model.clear();
    for (String data : myData) {
      model.addElement(data);
    }
  }

  @Nullable
  public List<String> getData() {
    return myData;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @NotNull
  public static String createStringPresentation(@Nullable List<String> data) {
    return data == null ? "" : StringUtil.join(data, ",");
  }

  @NotNull
  public static List<String> createListPresentation(@Nullable String data) {
    if (data == null || data.trim().isEmpty()) {
      return emptyList();
    }
    return newArrayList(data.split(","));
  }

  public static void installListItemsDialogForTextField(@NotNull TextFieldWithBrowseButton uiField,
                                                        @NotNull Producer<ListItemsDialogWrapper> createDialog) {
    uiField.getTextField().setEditable(false);
    uiField.setButtonIcon(PlatformIcons.OPEN_EDIT_DIALOG_ICON);
    uiField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ListItemsDialogWrapper tagListDialog = createDialog.produce();
        tagListDialog.setData(createListPresentation(uiField.getText()));
        if (tagListDialog.showAndGet()) {
          uiField.setText(createStringPresentation(tagListDialog.getData()));
        }
      }
    });
  }
}
