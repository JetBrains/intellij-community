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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

public class IJSwingUtilities {
  public static void invoke(Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL);
    }
  }

  /**
   * @return true if javax.swing.SwingUtilities.findFocusOwner(component) != null
   */
  public static boolean hasFocus(Component component) {
    Component focusOwner = findFocusOwner(component);
    return focusOwner != null;
  }

  private static Component findFocusOwner(Component c) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    // verify focusOwner is a descendant of c
    for (Component temp = focusOwner; temp != null; temp = (temp instanceof Window) ? null : temp.getParent())
    {
      if (temp == c) {
        return focusOwner;
      }
    }

    return null;
  }

  /**
   * @return true if window ancestor of component was most recent focused window and most recent focused component
   * in that window was descended from component
   */
  public static boolean hasFocus2(Component component) {
    WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
    Window activeWindow=null;
    if (windowManager != null) {
      activeWindow = windowManager.getMostRecentFocusedWindow();
    }
    if(activeWindow==null){
      return false;
    }
    Component focusedComponent = windowManager.getFocusedComponent(activeWindow);
    if (focusedComponent == null) {
      return false;
    }

    return SwingUtilities.isDescendingFrom(focusedComponent, component);
  }

  /**
   * This method is copied from <code>SwingUtilities</code>.
   * Returns index of the first occurrence of <code>mnemonic</code>
   * within string <code>text</code>. Matching algorithm is not
   * case-sensitive.
   *
   * @param text The text to search through, may be null
   * @param mnemonic The mnemonic to find the character for.
   * @return index into the string if exists, otherwise -1
   */
  public static int findDisplayedMnemonicIndex(String text, int mnemonic) {
    if (text == null || mnemonic == '\0') {
      return -1;
    }

    char uc = Character.toUpperCase((char)mnemonic);
    char lc = Character.toLowerCase((char)mnemonic);

    int uci = text.indexOf(uc);
    int lci = text.indexOf(lc);

    if (uci == -1) {
      return lci;
    } else if(lci == -1) {
      return uci;
    } else {
      return (lci < uci) ? lci : uci;
    }
  }

  public static Iterator<Component> getParents(final Component component) {
    return new Iterator<Component>() {
      private Component myCurrent = component;
      public boolean hasNext() {
        return myCurrent != null && myCurrent.getParent() != null;
      }

      public Component next() {
        myCurrent = myCurrent.getParent();
        return myCurrent;
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * @param component - parent component, won't be reached by iterator.
   * @return Component tree traverse {@link Iterator}.
   */
  public static Iterator<Component> getChildren(final Container component) {
    return new Iterator<Component>() {
      private Container myCurrentParent = component;
      private final TIntStack myState = new TIntStack();
      private int myCurrentIndex = 0;

      public boolean hasNext() {
        return hasNextChild();
      }

      public Component next() {
        Component next = myCurrentParent.getComponent(myCurrentIndex);
        myCurrentIndex++;
        if (next instanceof Container) {
          Container container = ((Container)next);
          if (container.getComponentCount() > 0) {
            myState.push(myCurrentIndex);
            myCurrentIndex = 0;
            myCurrentParent = container;
          }
        }
        while (!hasNextChild()) {
          if (myState.size() == 0) break;
          myCurrentIndex = myState.pop();
          myCurrentParent = myCurrentParent.getParent();
        }
        return next;
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

      private boolean hasNextChild() {
        return myCurrentParent.getComponentCount() > myCurrentIndex;
      }
    };
  }

  @Nullable
  public static <T extends Component> T findParentOfType(Component focusOwner, Class<T> aClass) {
    return (T)ContainerUtil.find(getParents(focusOwner), (FilteringIterator.InstanceOf<T>)FilteringIterator.instanceOf(aClass));
  }
}