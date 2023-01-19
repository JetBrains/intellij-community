// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class VisibilityWatcher extends ComponentAdapter implements PropertyChangeListener{
  @NonNls protected static final String ANCESTOR_PROPERTY_NAME = "ancestor";

  @Override
  public final void componentHidden(final ComponentEvent e){
    visibilityChanged();
  }

  @Override
  public final void componentShown(final ComponentEvent e){
    visibilityChanged();
  }

  @Override
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
