// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import static com.intellij.util.IJSwingUtilities.getFocusedComponentInWindowOrSelf;

@Deprecated(forRemoval = true)
class ButtonToolbarImpl extends JPanel {
  private final String myPlace;
  private final PresentationFactory myPresentationFactory;
  private final ArrayList<ActionJButton> myActions = new ArrayList<>();

  ButtonToolbarImpl(@NotNull String place, @NotNull ActionGroup actionGroup) {
    super(new GridBagLayout());
    myPlace = place;
    myPresentationFactory = new PresentationFactory();

    initButtons(actionGroup);

    updateActions();
    ActionManagerEx.getInstanceEx().addTimerListener(new WeakTimerListener(new MyTimerListener()));
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
  }

  private void initButtons(@NotNull ActionGroup actionGroup) {
    final AnAction[] actions = actionGroup.getChildren(null);

    if (actions.length > 0) {
      int gridx = 0;


      add(// left strut
                Box.createHorizontalGlue(),
                new GridBagConstraints(gridx++, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                        new Insets(8, 0, 0, 0), 0, 0));
      JPanel buttonsPanel = createButtons(actions);
      //noinspection UnusedAssignment
      add(buttonsPanel,
                new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                       new Insets(8, 0, 0, 0), 0, 0));
    }
  }

  private JPanel createButtons(AnAction[] actions) {
    JPanel buttonsPanel = new JPanel(new GridLayout(1, actions.length, 5, 0));
    for (final AnAction action : actions) {
      ActionJButton button = new ActionJButton(action);
      myActions.add(button);
      buttonsPanel.add(button);
    }
    return buttonsPanel;
  }

  public JComponent getComponent() {
    return this;
  }

  @NotNull
  private DataContext getDataContext() {
    Component target = getFocusedComponentInWindowOrSelf(this);
    return DataManager.getInstance().getDataContext(target);
  }

  private class ActionJButton extends JButton {
    private final AnAction myAction;

    ActionJButton(final AnAction action) {
      super(action.getTemplatePresentation().getText(true));
      myAction = action;
      setMnemonic(action.getTemplatePresentation().getMnemonic());
      setDisplayedMnemonicIndex(action.getTemplatePresentation().getDisplayedMnemonicIndex());

      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          AnActionEvent event = new AnActionEvent(
            null,
            getDataContext(),
            myPlace,
            myPresentationFactory.getPresentation(action),
            ActionManager.getInstance(),
            e.getModifiers()
          );
          if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
          }
        }
      });

    }

    public void updateAction(final DataContext dataContext) {
      AnActionEvent event = new AnActionEvent(
        null,
        dataContext,
        myPlace,
        myPresentationFactory.getPresentation(myAction),
        ActionManager.getInstance(),
        0
      );
      event.setInjectedContext(myAction.isInInjectedContext());
      myAction.update(event);
      setVisible(event.getPresentation().isVisible());
      setEnabled(event.getPresentation().isEnabled());
    }
  }

  private final class MyTimerListener implements TimerListener {
    @Override
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(ButtonToolbarImpl.this);
    }

    @Override
    public void run() {
      if (!isShowing()) {
        return;
      }

      Window mywindow = SwingUtilities.windowForComponent(ButtonToolbarImpl.this);
      if (mywindow != null && !mywindow.isActive()) return;

      // do not update when a popup menu is shown (if popup menu contains action which is also in the toolbar, it should not be enabled/disabled)
      final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog

      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog) {
        final Dialog dialog = (Dialog)window;
        if (dialog.isModal() && !SwingUtilities.isDescendingFrom(ButtonToolbarImpl.this, dialog)) {
          return;
        }
      }

      updateActions();
    }
  }

  private void updateActions() {
    DataContext dataContext = getDataContext();
    for (ActionJButton action : myActions) {
      action.updateAction(dataContext);
    }

    repaint();
  }
}
