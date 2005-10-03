package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.peer.PeerFactory;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Iterator;

import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */

public class TagsPanel extends JPanel implements TableCellRenderer{

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagsPanel");

  private final JLabel myTextLabel = new JLabel();

  private final JLabel myMoreLabel = new JLabel(MORE_LABEL_TEXT){
    private final Cursor myCursor = new Cursor(Cursor.HAND_CURSOR);
    public Cursor getCursor() {
      return myCursor;
    }
  };

  private Collection myTags;
  private final JList myList = new JList();
  private final Project myProject;
  @NonNls private static final String MORE_LABEL_TEXT = "<html><b>(...)</b></html>";


  public TagsPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    add(myTextLabel, BorderLayout.CENTER);
    add(myMoreLabel, BorderLayout.EAST);

    myMoreLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        showTags();
      }

      public void mousePressed(MouseEvent e) {
        showTags();
      }
    });
  }

  private void showTags() {
    DefaultListModel model = new DefaultListModel();
    myList.setModel(model);
    for (Iterator each = myTags.iterator(); each.hasNext();) {
      model.addElement(each.next());
    }
    Rectangle bounds = myMoreLabel.getBounds();
    Point location = new Point(bounds.x + 20, bounds.y);
    SwingUtilities.convertPointToScreen(location, this);
    PeerFactory.getInstance().getUIHelper().showListPopup(com.intellij.CvsBundle.message("list.popup.text.tags"), myList, EmptyRunnable.getInstance(), myProject, location.x, location.y);
  }

  public void setTags(Collection tags) {
    myTags = tags;
    myMoreLabel.setVisible(myTags.size() > 1);
    if (myTags.size() > 0)
      myTextLabel.setText(myTags.iterator().next().toString());
    revalidate();
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setSelected(isSelected, table);
    if (!(value instanceof Collection)) {
      LOG.info("getTableCellRendererComponent: " + value == null ? null : value.toString());
      return this;
    }
    final Collection tags = (Collection) value;
    setTags(tags);
    return this;
  }

  public void setSelected(boolean isSelected, JTable table){
    if (isSelected) {
      setBackground(table.getSelectionBackground());
      setForeground(table.getSelectionForeground());
    } else {
      setBackground(table.getBackground());
      setForeground(table.getForeground());
    }

    updateLabel(myTextLabel, isSelected, table);
    updateLabel(myMoreLabel, isSelected, table);
  }

  private void updateLabel(JLabel label, boolean isSelected, JTable table) {
    if (isSelected) {
      label.setBackground(table.getSelectionBackground());
      label.setForeground(table.getSelectionForeground());
    } else {
      label.setBackground(table.getBackground());
      label.setForeground(table.getForeground());
    }
  }
}
