// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.async;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.NextStepHandler;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AsyncPopupImpl extends WizardPopup implements Runnable {

  private final Future<PopupStep> myFuture;
  private final Object myParentValue;
  private final NextStepHandler myCallBackParent;
  private final Alarm myAlarm;
  private JPanel myPanel;

  public AsyncPopupImpl(@Nullable WizardPopup parent, @NotNull AsyncPopupStep step, Object parentValue) {
    super(parent, step);

    if (!(parent instanceof NextStepHandler)) throw new IllegalArgumentException("parent must be NextStepHandler");

    myCallBackParent = (NextStepHandler)parent;
    myParentValue = parentValue;

    myFuture = ApplicationManager.getApplication().executeOnPooledThread(step);

    myAlarm = new Alarm(this);
    myAlarm.addRequest(this, 200);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        if (!myFuture.isCancelled() && !myFuture.isDone()) {
          myFuture.cancel(false);
        }
      }
    });
  }

  @Override
  public void run() {
    if (myFuture.isCancelled()) return;

    if (myFuture.isDone()) {
      goBack();
      try {
        myCallBackParent.handleNextStep(myFuture.get(), myParentValue);
      }
      catch (InterruptedException e) {
        //ignore
      }
      catch (ExecutionException e) {
        //ignore
      }
      return;
    }
    myAlarm.addRequest(this, 200);
  }

  @Override
  protected JComponent createContent() {
    if (myPanel != null) return myPanel;
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(new AsyncProcessIcon("Async Popup Step"), BorderLayout.WEST);
    myPanel.add(new JBLabel("Loading..."), BorderLayout.CENTER);
    myPanel.setBorder(new EmptyBorder(UIUtil.getListCellPadding()));
    myPanel.setBackground(UIUtil.getListBackground());
    myPanel.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        goBack();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);
    return myPanel;
  }

  @Override
  protected JComponent getPreferredFocusableComponent() {
    return createContent();
  }

  @Override
  protected InputMap getInputMap() {
    return null;
  }

  @Override
  protected ActionMap getActionMap() {
    return null;
  }

  @Override
  protected void onChildSelectedFor(Object value) {
  }
}
