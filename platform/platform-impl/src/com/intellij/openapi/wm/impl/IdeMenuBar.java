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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.WeakTimerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made non-final public for Fabrique
public class IdeMenuBar extends JMenuBar{
  private final MyTimerListener myTimerListener;
  private ArrayList<AnAction> myVisibleActions;
  private ArrayList<AnAction> myNewVisibleActions;
  private final MenuItemPresentationFactory myPresentationFactory;
  private final DataManager myDataManager;
  private final ActionManagerEx myActionManager;
  private final Disposable myDisposable = Disposer.newDisposable();

  public IdeMenuBar(ActionManagerEx actionManager, DataManager dataManager){
    myActionManager = actionManager;
    myTimerListener=new MyTimerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myPresentationFactory = new MenuItemPresentationFactory();
    myDataManager = dataManager;
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify(){
    super.addNotify();
    updateMenuActions();
    // Add updater for menus
    myActionManager.addTimerListener(1000,new WeakTimerListener(myActionManager,myTimerListener));
    UISettingsListener UISettingsListener = new UISettingsListener() {
      public void uiSettingsChanged(final UISettings source) {
        updateMnemonicsVisibility();
        myPresentationFactory.reset();
      }
    };
    UISettings.getInstance().addUISettingsListener(UISettingsListener, myDisposable);
    Disposer.register(ApplicationManager.getApplication(), myDisposable);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public void removeNotify(){
    Disposer.dispose(myDisposable);
    super.removeNotify();
  }

  void updateMenuActions() {
    myNewVisibleActions.clear();
    final DataContext dataContext = ((DataManagerImpl)myDataManager).getDataContextTest(this);

    expandActionGroup(dataContext, myNewVisibleActions, myActionManager);

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI

      final boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

      final ArrayList<AnAction> temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      Color background = null;
      for (final AnAction action : myVisibleActions) {
        final ActionMenu menu = new ActionMenu(null, ActionPlaces.MAIN_MENU, (ActionGroup)action, myPresentationFactory, !UISettings.getInstance().DISABLE_MNEMONICS);
        add(menu);
        background = menu.getBackground();
      }

      if (background != null) {
        setBackground(background);
      }

      updateMnemonicsVisibility();
      validate();

      if (changeBarVisibility) {
        invalidate();
        final JFrame frame = (JFrame)SwingUtilities.getAncestorOfClass(JFrame.class, this);
        if (frame != null) {
          frame.validate();
        }
      }
    }
  }

  /**
   * Hacks a problem under Alloy LaF which draws menubar in different background menu items are drawn in.
   */
  @Override public void updateUI() {
    super.updateUI();
    if (getMenuCount() > 0) {
      final JMenu menu = getMenu(0);
      if (menu != null) {  // hack for Substance LAF compatibility
        menu.updateUI();
        setBackground(menu.getBackground());
      }
    }
  }

  private void expandActionGroup(final DataContext context,
                                 final ArrayList<AnAction> newVisibleActions,
                                 ActionManager actionManager) {
    final ActionGroup mainActionGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU);
    if (mainActionGroup == null) return;
    final AnAction[] children = mainActionGroup.getChildren(null);
    for (final AnAction action : children) {
      if (!(action instanceof ActionGroup)) {
        continue;
      }
      final Presentation presentation = myPresentationFactory.getPresentation(action);
      final AnActionEvent e = new AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation,
                                                actionManager,
                                                0);
      e.setInjectedContext(action.isInInjectedContext());
      action.update(e);
      if (presentation.isVisible()) { // add only visible items
        newVisibleActions.add(action);
      }
    }
  }

  private void updateMnemonicsVisibility(){
    final boolean enabled= !UISettings.getInstance().DISABLE_MNEMONICS;
    for(int i=0;i<getMenuCount();i++){
      ((ActionMenu)getMenu(i)).setMnemonicEnabled(enabled);
    }
  }

  private final class MyTimerListener implements TimerListener{
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(IdeMenuBar.this);
    }

    public void run(){
      if(!isShowing()){
        return;
      }

      Window mywindow = SwingUtilities.windowForComponent(IdeMenuBar.this);
      if (mywindow != null && !mywindow.isActive()) return;

      // do not update when a popup menu is shown (if popup menu contains action which is also in the menu bar, it should not be enabled/disabled)

      final MenuSelectionManager menuSelectionManager=MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if(selectedPath.length>0){
        return;
      }

      // don't update toolbar if there is currently active modal dialog

      final Window window=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if(window instanceof Dialog){
        if (((Dialog)window).isModal()){
          return;
        }
      }

      updateMenuActions();
    }
  }

}
