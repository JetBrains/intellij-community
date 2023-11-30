// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusListener;
import java.lang.ref.WeakReference;

/**
 * A class which tracks focus events for the given component and its children, grandchildren, etc
 */
public abstract class BaseFocusWatcher implements ContainerListener, FocusListener {
  private WeakReference<Component> myTopComponent;

  /**
   * @return top component on which focus watcher was installed.
   */
  public Component getTopComponent() {
    return SoftReference.dereference(myTopComponent);
  }

  @Override
  public final void componentAdded(ContainerEvent e) {
    Component component = e.getChild();
    if (component != null) {
      installImpl(component);
    }
  }

  @Override
  public final void componentRemoved(final ContainerEvent e) {
    Component removedChild = e.getChild();
    deinstall(removedChild, e);
  }

  public final void deinstall(final Component component) {
    deinstall(component, null);
  }

  private void deinstall(final Component component, @Nullable AWTEvent cause) {
    if (component == null) {
      return;
    }

    if (component instanceof Container container) {
      int componentCount = container.getComponentCount();
      for (int i = 0; i < componentCount; i++) {
        deinstall(container.getComponent(i));
      }
      container.removeContainerListener(this);
    }

    component.removeFocusListener(this);
    componentUnregistered(component, cause);
  }

  public final void install(@NotNull Component component) {
    myTopComponent = new WeakReference<>(component);
    installImpl(component);
  }

  private void installImpl(@NotNull Component component) {
    if (component instanceof Container container) {
      synchronized (container.getTreeLock()) {
        int componentCount = container.getComponentCount();
        for (int i = 0; i < componentCount; i++) {
          Component child = container.getComponent(i);
          if (child != null) {
            installImpl(child);
          }
        }
        container.addContainerListener(this);
      }
    }

    if (component instanceof JMenuItem || component instanceof JMenuBar) {
      return;
    }
    component.addFocusListener(this);
  }

  protected void componentUnregistered(Component component, @Nullable AWTEvent cause) {}
}
