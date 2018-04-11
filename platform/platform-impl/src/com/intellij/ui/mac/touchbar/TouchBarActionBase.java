// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.awt.*;

import static com.intellij.openapi.application.ModalityState.NON_MODAL;

public class TouchBarActionBase extends TouchBar {
  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  TouchBarActionBase(String touchbarName) {
    super(touchbarName);

    ActionManager.getInstance().addTimerListener(500, new TimerListener() {
      @Override
      public ModalityState getModalityState() {
        return NON_MODAL;
      }
      @Override
      public void run() {
        _updateActionItems();
      }
    });
  }

  TBItemAnActionButton addAnActionButton(String actId) {
    return addAnActionButton(ActionManager.getInstance().getAction(actId), true);
  }

  TBItemAnActionButton addAnActionButton(String actId, boolean hiddenWhenDisabled) {
    return addAnActionButton(ActionManager.getInstance().getAction(actId), hiddenWhenDisabled);
  }

  TBItemAnActionButton addAnActionButton(AnAction act, boolean hiddenWhenDisabled) {
    final String uid = String.format("%s.anActionButton.%d", myName, myCounter++);
    final TBItemAnActionButton butt = new TBItemAnActionButton(uid, act, hiddenWhenDisabled);
    myItems.add(butt);
    return butt;
  }

  private void _updateActionItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean layoutChanged = false;
    for (TBItem tbitem: myItems) {
      if (!(tbitem instanceof TBItemAnActionButton))
        continue;

      final TBItemAnActionButton item = (TBItemAnActionButton)tbitem;
      final AnAction act = item.getAnAction();
      final Presentation presentation = myPresentationFactory.getPresentation(act);
      final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      final Component focusOwner = focusManager.getFocusedWindow();
      final DataContext ctx = DataManagerImpl.getInstance().getDataContext(focusOwner);

      final AnActionEvent e = new AnActionEvent(
        null,
        ctx,
        ActionPlaces.TOUCHBAR_GENERAL,
        presentation,
        ActionManagerEx.getInstanceEx(),
        0
      );
      act.update(e);

      if (item.isAutoVisibility()) {
        final boolean itemVisibilityChanged = item.updateVisibility(presentation);
        if (itemVisibilityChanged)
          layoutChanged = true;
      }
      item.updateView(presentation);
    }

    if (layoutChanged)
      selectVisibleItemsToShow();
  }
}
