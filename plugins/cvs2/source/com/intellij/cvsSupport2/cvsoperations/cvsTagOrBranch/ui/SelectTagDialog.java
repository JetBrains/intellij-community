package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class SelectTagDialog extends DialogWrapper {
  private final Collection<JList> myLists =  new ArrayList<JList>();
  private JPanel myPanel;
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
    list.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });

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
