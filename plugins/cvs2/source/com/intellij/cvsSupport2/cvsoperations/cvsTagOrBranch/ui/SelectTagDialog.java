package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
public class SelectTagDialog extends DialogWrapper {
  private final Collection<JList> myLists =  new ArrayList<JList>();
  private JPanel myPanel;
  public static final String EXISTING_REVISIONS = "Existing revisions:";
  public static final String EXISTING_TAGS = "Existing tags:";

  public SelectTagDialog(Collection<String> tags, Collection<String> revisions) {
    super(true);
    myPanel = new JPanel(new GridLayout(1, 0, 4, 8));

    if (tags.isEmpty()){
      createList("Select Revision", revisions, BorderLayout.CENTER, EXISTING_REVISIONS);
    }
    else if (revisions.isEmpty()){
      createList("Select Tag", tags, BorderLayout.CENTER, EXISTING_TAGS);
    }
    else{
      createList("Select Revision or Tag", revisions, BorderLayout.EAST, EXISTING_REVISIONS);
      createList("Select Revision or Tag", tags, BorderLayout.WEST, EXISTING_TAGS);
    }

    setOkEnabled();
    init();
  }

  private void createList(String title, Collection<String> data, String place, String listDescription) {
    setTitle(title);
    final JList list = new JList();
    myLists.add(list);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (list.getSelectedValue() != null)
          cancelOtherSelections(list);
        setOkEnabled();
      }
    });

    fillList(data, list);


    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(new JLabel(listDescription, JLabel.LEFT), BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER);
    myPanel.add(panel);
  }

  private void cancelOtherSelections(JList list) {
    for (Iterator each = myLists.iterator(); each.hasNext();) {
      JList jList = (JList)each.next();
      if (jList == list) continue;
      jList.getSelectionModel().clearSelection();
    }
  }

  private void setOkEnabled() {
    setOKActionEnabled(hasSelection());
  }

  private boolean hasSelection() {
    for (Iterator iterator = myLists.iterator(); iterator.hasNext();) {
      JList list = (JList)iterator.next();
      if (list.getSelectedValue() != null) return true;
    }
    return false;
  }

  public String getTag() {
    for (Iterator iterator = myLists.iterator(); iterator.hasNext();) {
      JList list = (JList)iterator.next();
      Object selectedValue = list.getSelectedValue();
      if (selectedValue != null) return selectedValue.toString();
    }
    return null;
  }

  private void fillList(Collection tags, JList list) {
    DefaultListModel model = new DefaultListModel();
    list.setModel(model);

    for (Iterator each = tags.iterator(); each.hasNext();) {
      model.addElement(each.next());
    }
    if (!tags.isEmpty())
      list.getSelectionModel().addSelectionInterval(0, 0);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
