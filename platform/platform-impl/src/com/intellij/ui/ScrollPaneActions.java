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

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

import static com.intellij.util.ui.UIUtil.getParentOfType;

/**
 * @author Sergey.Malenkov
 */
public abstract class ScrollPaneActions extends SwingActionDelegate {
  private ScrollPaneActions(String actionId) {
    super(actionId);
  }

  @Override
  protected JComponent getComponent(AnActionEvent event) {
    return getParentOfType(JScrollPane.class, super.getComponent(event));
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
