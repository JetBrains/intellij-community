package com.intellij.cvsSupport2.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * author: lesya
 */
public class SelectFromListDialog extends DialogWrapper {
  private final ToStringAspect myToStringAspect;
  private final DefaultListModel myModel = new DefaultListModel();
  private final JList myList = new JList(myModel);

  public SelectFromListDialog(Project project,
                              Object[] objects,
                              ToStringAspect toStringAspect,
                              String title,
                              int selectionMode) {
    super(project, true);
    myToStringAspect = toStringAspect;
    myList.setSelectionMode(selectionMode);
    setTitle(title);

    for (int i = 0; i < objects.length; i++) {
      myModel.addElement(objects[i]);
    }

    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(myList.getSelectedValues().length > 0);
      }
    });

    myList.setSelectedIndex(0);

    myList.setCellRenderer(new ColoredListCellRenderer(){
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(myToStringAspect.getToStirng(value),
               new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
    });


    init();
  }

  protected JComponent createCenterPanel() {
    return ScrollPaneFactory.createScrollPane(myList);
  }

  public interface ToStringAspect {
    String getToStirng(Object obj);
  }

  public Object[] getSelection(){
    if (!isOK()) return null;
    return myList.getSelectedValues();
  }
}
