package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.ui.ListToolTipHandler;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * @author nik
 */
public abstract class DebuggerFramesList extends JList implements OccurenceNavigator {
  public DebuggerFramesList() {
    super(new DefaultListModel());
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(createListRenderer());
    ListToolTipHandler.install(this);
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          onFrameChanged(getSelectedValue());
        }
      }
    });
  }

  public DefaultListModel getModel() {
    return (DefaultListModel)super.getModel();
  }

  public void clear() {
    getModel().clear();
  }

  public int getElementCount() {
    return getModel().getSize();
  }

  public String getNextOccurenceActionName() {
    return XDebuggerBundle.message("action.next.frame.text");
  }

  public String getPreviousOccurenceActionName() {
    return XDebuggerBundle.message("action.previous.frame.text");
  }

  public OccurenceInfo goNextOccurence() {
    setSelectedIndex(getSelectedIndex() + 1);
    return createInfo();
  }

  public OccurenceInfo goPreviousOccurence() {
    setSelectedIndex(getSelectedIndex() - 1);
    return createInfo();
  }

  private OccurenceInfo createInfo() {
    return OccurenceInfo.position(getSelectedIndex(), getElementCount());
  }

  public boolean hasNextOccurence() {
    return getSelectedIndex() < getElementCount() - 1;
  }

  public boolean hasPreviousOccurence() {
    return getSelectedIndex() > 0;
  }

  protected abstract ListCellRenderer createListRenderer();

  protected abstract void onFrameChanged(final Object selectedValue);
}
