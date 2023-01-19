// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.accessibility;

import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;

public abstract class AccessibleContextDelegateWithContextMenu extends AccessibleContextDelegate {
  private AccessibleAction myAction = null;

  public AccessibleContextDelegateWithContextMenu(@NotNull AccessibleContext context) {
    super(context);
  }

  @Override
  public AccessibleAction getAccessibleAction() {
    if (myAction == null) {
      myAction = new AccessibleAction() {
        @Override
        public int getAccessibleActionCount() {
          AccessibleContext ac = getDelegate();
          AccessibleAction aa = ac.getAccessibleAction();
          return aa != null ? aa.getAccessibleActionCount() + 1 : 1;
        }

        @Override
        public String getAccessibleActionDescription(int i) {
          if (i < 0 || i > getAccessibleActionCount()) return null;
          if (getAccessibleActionCount() != 1 && i < getAccessibleActionCount() - 1) {
            return getDelegate().getAccessibleAction().getAccessibleActionDescription(i);
          }

          return AccessibleAction.TOGGLE_POPUP;
        }

        @Override
        public boolean doAccessibleAction(int i) {
          if (i < 0 || i > getAccessibleActionCount()) return false;
          if (getAccessibleActionCount() != 1 && i < getAccessibleActionCount() - 1) {
            return getDelegate().getAccessibleAction().doAccessibleAction(i);
          }

          doShowContextMenu();
          return true;
        }
      };
    }
    return myAction;
  }

  protected abstract void doShowContextMenu();
}
