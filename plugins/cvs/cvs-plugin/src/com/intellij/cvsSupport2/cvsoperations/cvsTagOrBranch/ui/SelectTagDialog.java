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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class SelectTagDialog extends DialogWrapper {
  private final Collection<JList> myLists = new ArrayList<>();
  private final JPanel myPanel;
  public static final String EXISTING_REVISIONS = CvsBundle.message("label.existing.revisions");
  public static final String EXISTING_TAGS = CvsBundle.message("label.existing.tags");

  public SelectTagDialog(Collection<String> tags, Collection<String> revisions) {
    super(true);
    myPanel = new JPanel(new GridLayout(1, 0, 4, 8));

    if (tags.isEmpty()){
      createList(CvsBundle.message("dialog.title.select.revision"), revisions, EXISTING_REVISIONS);
    }
    else if (revisions.isEmpty()){
      createList(CvsBundle.message("operation.name.select.tag"), tags, EXISTING_TAGS);
    }
    else{
      createList(CvsBundle.message("dialog.title.select.revision.or.tag"), revisions, EXISTING_REVISIONS);
      createList(CvsBundle.message("dialog.title.select.revision.or.tag"), tags, EXISTING_TAGS);
    }

    setOkEnabled();
    init();
  }

  private void createList(String title, Collection<String> data, String listDescription) {
    setTitle(title);
    final JList list = new JBList();
    myLists.add(list);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (list.getSelectedValue() != null)
          cancelOtherSelections(list);
        setOkEnabled();
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (isOKActionEnabled()) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(list);

    fillList(data, list);


    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(new JLabel(listDescription, JLabel.LEFT), BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER);
    myPanel.add(panel);
  }

  private void cancelOtherSelections(JList list) {
    for (final JList jlist : myLists) {
      if (jlist == list) continue;
      jlist.getSelectionModel().clearSelection();
    }
  }

  private void setOkEnabled() {
    setOKActionEnabled(hasSelection());
  }

  private boolean hasSelection() {
    for (final JList list : myLists) {
      if (list.getSelectedValue() != null) return true;
    }
    return false;
  }

  @Nullable
  public String getTag() {
    for (final JList list : myLists) {
      Object selectedValue = list.getSelectedValue();
      if (selectedValue != null) return selectedValue.toString();
    }
    return null;
  }

  private static void fillList(Collection<String> tags, JList list) {
    DefaultListModel model = new DefaultListModel();
    list.setModel(model);

    for (final String tag : tags) {
      model.addElement(tag);
    }
    if (!tags.isEmpty())
      list.getSelectionModel().addSelectionInterval(0, 0);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
