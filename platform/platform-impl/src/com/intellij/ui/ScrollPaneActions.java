/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ui.UIUtil;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;

/**
 * @author Sergey.Malenkov
 */
public abstract class ScrollPaneActions extends AnAction {
  private final String mySwingActionId;

  private ScrollPaneActions(String actionId) {
    mySwingActionId = actionId;
  }

  @Override
  public void update(AnActionEvent event) {
    JScrollPane pane = getScrollPane(event);
    event.getPresentation().setEnabled(pane != null && null != getSwingAction(pane));
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    JScrollPane pane = getScrollPane(event);
    if (pane == null) return;
    Action action = getSwingAction(pane);
    if (action == null) return;
    action.actionPerformed(new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, mySwingActionId));
  }

  private JScrollPane getScrollPane(AnActionEvent event) {
    Component component = event.getData(CONTEXT_COMPONENT);
    return UIUtil.getParentOfType(JScrollPane.class, component);
  }

  private Action getSwingAction(JScrollPane pane) {
    ActionMap map = pane.getActionMap();
    return map == null ? null : map.get(mySwingActionId);
  }

  public static final class Home extends ScrollPaneActions {
    public Home() {
      super("scrollHome");
    }
  }

  public static final class End extends ScrollPaneActions {
    public End() {
      super("scrollEnd");
    }
  }

  public static final class Up extends ScrollPaneActions {
    public Up() {
      super("unitScrollUp");
    }
  }

  public static final class Down extends ScrollPaneActions {
    public Down() {
      super("unitScrollDown");
    }
  }

  public static final class Left extends ScrollPaneActions {
    public Left() {
      super("unitScrollLeft");
    }
  }

  public static final class Right extends ScrollPaneActions {
    public Right() {
      super("unitScrollRight");
    }
  }

  public static final class PageUp extends ScrollPaneActions {
    public PageUp() {
      super("scrollUp");
    }
  }

  public static final class PageDown extends ScrollPaneActions {
    public PageDown() {
      super("scrollDown");
    }
  }

  public static final class PageLeft extends ScrollPaneActions {
    public PageLeft() {
      super("scrollLeft");
    }
  }

  public static final class PageRight extends ScrollPaneActions {
    public PageRight() {
      super("scrollRight");
    }
  }
}
