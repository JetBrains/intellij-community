// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

public class DialogResizerAction extends ToggleAction implements DumbAware {

  private DialogResizer myDialogResizer;
  private Integer myHeight = null;
  private Integer myWeight = null;

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myDialogResizer != null;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    if (state) {
      DialogResizerWindow dialogResizerWindow = new DialogResizerWindow(myHeight, myWeight, false);
      boolean isOK = dialogResizerWindow.showAndGet();
      if (isOK) {
        myHeight = dialogResizerWindow.getHeight();
        myWeight = dialogResizerWindow.getWeight();
        if (myDialogResizer == null) {
          myDialogResizer = new DialogResizer();
        }
        Notifications.Bus.notify(new Notification("Resizer", "Dialog Resizer", "Control-Shift-Click to resize the component!",
                                                  NotificationType.INFORMATION, null));
      }
    }
    else {
      DialogResizer dialogResizer = myDialogResizer;
      myDialogResizer = null;
      if (dialogResizer != null) {
        Disposer.dispose(dialogResizer);
      }
    }
  }

  private static class DialogResizerWindow extends DialogWrapper {

    private JBTextField myHeightTextField;
    private JBTextField myWeightTextField;
    private final Integer myHeight;
    private final Integer myWeight;


    private Integer getHeight() {
      return Integer.valueOf(myHeightTextField.getText());
    }

    private Integer getWeight() {
      return Integer.valueOf(myWeightTextField.getText());
    }


    protected DialogResizerWindow(Integer height, Integer weight, boolean canBeParent) {
      super(canBeParent);
      myHeight = height;
      myWeight = weight;
      setTitle("Required Size");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());

      JPanel firstLine = new JPanel(new BorderLayout());
      firstLine.add(new JBLabel("Height: "), BorderLayout.WEST);
      myHeightTextField = new JBTextField(1);
      if (myHeight != null) myHeightTextField.setText(myHeight.toString());
      firstLine.add(myHeightTextField, BorderLayout.CENTER);
      panel.add(firstLine, BorderLayout.SOUTH);

      JPanel secondLine = new JPanel(new BorderLayout());
      secondLine.add(new JBLabel("Width:  "), BorderLayout.WEST);
      myWeightTextField = new JBTextField(1);
      if (myWeight != null) myWeightTextField.setText(myWeight.toString());

      secondLine.add(myWeightTextField, BorderLayout.CENTER);
      panel.add(secondLine);
      return panel;
    }
  }

  private class DialogResizer implements AWTEventListener, Disposable {
    DialogResizer() {
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void dispose() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof MouseEvent) {
        processMouseEvent((MouseEvent)event);
      }
    }

    private void processMouseEvent(MouseEvent me) {
      if (!me.isShiftDown() || !me.isControlDown()) return;
      if (me.getClickCount() != 1 || me.isPopupTrigger()) return;
      me.consume();
      if (me.getID() != MouseEvent.MOUSE_RELEASED) return;
      Component component = me.getComponent();
      Component parent = component.getParent();
      while (!(parent instanceof Dialog) && !(parent instanceof Frame)) {
        parent = parent.getParent();
        if (parent == null) return;
      }
      parent.setSize(DialogResizerAction.this.myWeight, DialogResizerAction.this.myHeight);
    }
  }
}
