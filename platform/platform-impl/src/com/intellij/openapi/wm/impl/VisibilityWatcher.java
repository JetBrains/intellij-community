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

import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class VisibilityWatcher extends ComponentAdapter implements PropertyChangeListener{
  @NonNls protected static final String ANCESTOR_PROPERTY_NAME = "ancestor";

  public final void componentHidden(final ComponentEvent e){
    visibilityChanged();
  }

  public final void componentShown(final ComponentEvent e){
    visibilityChanged();
  }

  public final void propertyChange(final PropertyChangeEvent e){
    if(ANCESTOR_PROPERTY_NAME.equals(e.getPropertyName())){
      final Component oldAncestor=(Component)e.getOldValue();
      deinstall(oldAncestor);
      final Component newAncestor=(Component)e.getNewValue();
      install(newAncestor);
      visibilityChanged();
    }else{
      throw new IllegalArgumentException("unknown propertyName: "+e.getPropertyName());
    }
  }

  public final void install(Component component){
    while(component!=null){
      component.removePropertyChangeListener(ANCESTOR_PROPERTY_NAME,this); // it prevent double registering
      component.addPropertyChangeListener(ANCESTOR_PROPERTY_NAME,this);

      component.removeComponentListener(this); // it prevent double registering
      component.addComponentListener(this);
      component=component.getParent();
    }
  }

  public void deinstall(Component component){
    while(component!=null){
      component.removePropertyChangeListener(ANCESTOR_PROPERTY_NAME,this);
      component.removeComponentListener(this);
      component=component.getParent();
    }
  }

  /**
   * Invokes every time component changes its visibility. It means one of parent component
   * change visibility or hierarchy is connected/disconnected to/from peer.
   */
  public abstract void visibilityChanged();
}
