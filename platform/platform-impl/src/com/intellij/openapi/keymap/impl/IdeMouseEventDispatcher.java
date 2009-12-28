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
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Map;

/**
 * TODO[vova] rewrite comments
 * Current implementation of the dispatcher is intended to filter mouse event addressed to
 * the editor. Also it allows to map middle mouse's button to some action.
 *
 * @author Vladimir Kondratyev
 */
public final class IdeMouseEventDispatcher{
  private final PresentationFactory myPresentationFactory;
  private final ArrayList<AnAction> myActions;

  private final Map<Container, Integer> myRootPane2BlockedId = new HashMap<Container, Integer>();

  public IdeMouseEventDispatcher(){
    myPresentationFactory=new PresentationFactory();
    myActions=new ArrayList<AnAction>(1);
  }

  private void fillActionsList(Component component,MouseShortcut shortcut,boolean isModalContext){
    myActions.clear();

    // here we try to find "local" shortcuts

    if(component instanceof JComponent){
      ArrayList<AnAction> listOfActions = (ArrayList<AnAction>)((JComponent)component).getClientProperty(AnAction.ourClientProperty);
      if (listOfActions != null) {
        for (AnAction action : listOfActions) {
          Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
          for (Shortcut shortcut1 : shortcuts) {
            if (shortcut.equals(shortcut1) && !myActions.contains(action)) {
              myActions.add(action);
            }
          }
        }
        // once we've found a proper local shortcut(s), we exit
        if (!myActions.isEmpty()) {
          return;
        }
      }
    }

    // search in main keymap

    if (KeymapManagerImpl.ourKeymapManagerInitialized) {
      KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        Keymap keymap = keymapManager.getActiveKeymap();
        String[] actionIds=keymap.getActionIds(shortcut);

        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : actionIds) {
          AnAction action = actionManager.getAction(actionId);
          if (action == null) {
            continue;
          }
          if (isModalContext && !action.isEnabledInModalContext()) {
            continue;
          }
          if (!myActions.contains(action)) {
            myActions.add(action);
          }
        }
      }
    }
  }

  /**
   * @return <code>true</code> if and only if the passed event is already dispatched by the
   * <code>IdeMouseEventDispatcher</code> and there is no need for any other processing of the event.
   * If the method returns <code>false</code> then it means that the event should be delivered
   * to normal event dispatching.
   */
  public boolean dispatchMouseEvent(MouseEvent e){
    boolean toIgnore = false;

    if (!(e.getID() == MouseEvent.MOUSE_PRESSED ||
          e.getID() == MouseEvent.MOUSE_RELEASED ||
          e.getID() == MouseEvent.MOUSE_CLICKED)) {
      toIgnore = true;
    }


    if(
      e.isConsumed()||
      e.isPopupTrigger()||
      MouseEvent.MOUSE_RELEASED!=e.getID()||
      e.getClickCount()<1  ||// TODO[vova,anton] is it possible. it seems that yes! but how???
      e.getButton() == MouseEvent.NOBUTTON // See #16995. It did happen
    ){
      toIgnore = true;
    }


    JRootPane root = findRoot(e);

    if (root != null) {
      final Integer lastId = myRootPane2BlockedId.get(root);
      if (lastId != null) {
        if (e.getID() <= lastId.intValue()) {
          myRootPane2BlockedId.remove(root);
        } else {
          myRootPane2BlockedId.put(root, e.getID());
          return true;
        }
      }
    }

    if (toIgnore) return false;

    Component component=e.getComponent();
    if(component==null){
      throw new IllegalStateException("component cannot be null");
    }
    component=SwingUtilities.getDeepestComponentAt(component,e.getX(),e.getY());

    if (component instanceof IdeGlassPaneImpl) {
      component = ((IdeGlassPaneImpl)component).getTargetComponentFor(e);
    }

    if(component==null){ // do nothing if component doesn't contains specified point
      return false;
    }

    // avoid "cyclic component initialization error" in case of dialogs shown because of component initialization failure
    if (!KeymapManagerImpl.ourKeymapManagerInitialized) {
      return false;
    }

    MouseShortcut shortcut=new MouseShortcut(e.getButton(),e.getModifiersEx(),e.getClickCount());
    fillActionsList(component,shortcut,IdeKeyEventDispatcher.isModalContext(component));
    ActionManagerEx actionManager=ActionManagerEx.getInstanceEx();
    if (actionManager != null) {
      for(int i=0;i<myActions.size();i++){
        AnAction action=myActions.get(i);
        DataContext dataContext=DataManager.getInstance().getDataContext(component);
        Presentation presentation=myPresentationFactory.getPresentation(action);
        AnActionEvent actionEvent=new AnActionEvent(e,dataContext,ActionPlaces.MAIN_MENU,presentation,
                                                    ActionManager.getInstance(),
                                                    e.getModifiers());
        action.beforeActionPerformedUpdate(actionEvent);
        if(presentation.isEnabled()){
          actionManager.fireBeforeActionPerformed(action, dataContext, actionEvent);
          Component c = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
          if (c != null && !c.isShowing()) {
            continue;
          }
          action.actionPerformed(actionEvent);
          e.consume();
        }
      }
    }
    return false;
  }

  public void blockNextEvents(final MouseEvent e) {
    JRootPane root = findRoot(e);
    if (root == null) return;

    myRootPane2BlockedId.put(root, e.getID());
  }

  private JRootPane findRoot(MouseEvent e) {
    Component parent = UIUtil.findUltimateParent(e.getComponent());
    JRootPane root = null;
    if (parent instanceof JWindow) {
      root = ((JWindow)parent).getRootPane();
    } else if (parent instanceof JDialog) {
      root = ((JDialog)parent).getRootPane();
    } else if (parent instanceof JFrame) {
      root = ((JFrame)parent).getRootPane();
    }
    return root;
  }
}
