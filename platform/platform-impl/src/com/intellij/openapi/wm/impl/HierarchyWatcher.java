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
