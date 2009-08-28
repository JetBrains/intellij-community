package com.intellij.openapi.wm.impl;

import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class HierarchyWatcher implements ContainerListener{
  public final void componentAdded(final ContainerEvent e){
    install(e.getChild());
    hierarchyChanged(e);
  }

  public final void componentRemoved(final ContainerEvent e){
    final Component removedChild=e.getChild();
    deinstall(removedChild);
    hierarchyChanged(e);
  }

  private void install(final Component component){
    if(component instanceof Container){
      final Container container=(Container)component;
      final int componentCount=container.getComponentCount();
      for(int i=0;i<componentCount;i++){
        install(container.getComponent(i));
      }
      container.addContainerListener(this);
    }
  }

  private void deinstall(final Component component){
    if(component instanceof Container){
      final Container container=(Container)component;
      final int componentCount=container.getComponentCount();
      for(int i=0;i<componentCount;i++){
        deinstall(container.getComponent(i));
      }
      container.removeContainerListener(this);
    }
  }

  /**
   * Override this method to get notifications abot changes in component hierarchy.
   * <code>HierarchyWatcher</code> invokes this method each time one of the populated container changes.
   * @param e event which describes the changes.
   */
  protected abstract void hierarchyChanged(ContainerEvent e);
}
