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
package com.intellij.ui;

import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class ListenerUtil {
  public static void addFocusListener(Component component, FocusListener l) {
    component.addFocusListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addFocusListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeFocusListener(Component component, FocusListener l) {
    component.removeFocusListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeFocusListener(container.getComponent(i), l);
      }
    }
  }

  public static void addMouseListener(Component component, MouseListener l) {
    component.addMouseListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addMouseListener(container.getComponent(i), l);
      }
    }
  }

  public static void addClickListener(Component component, ClickListener l) {
    l.installOn(component);

    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addClickListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeClickListener(Component component, ClickListener l) {
    l.uninstall(component);

    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeClickListener(container.getComponent(i), l);
      }
    }
  }

  public static void addMouseMotionListener(Component c, MouseMotionListener l) {
    c.addMouseMotionListener(l);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        addMouseMotionListener(child, l);
      }
    }
  }

  public static void removeMouseListener(Component component, MouseListener l) {
    component.removeMouseListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeMouseListener(container.getComponent(i), l);
      }
    }
  }

  public static void addKeyListener(Component component, KeyListener l) {
    component.addKeyListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addKeyListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeKeyListener(Component component, KeyListener l) {
    component.removeKeyListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeKeyListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeMouseMotionListener(final Component component, final MouseMotionListener motionListener) {
    component.removeMouseMotionListener(motionListener);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeMouseMotionListener(container.getComponent(i), motionListener);
      }
    }
  }
}
