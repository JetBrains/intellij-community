
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
package com.intellij.ui.content.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.AlertIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ContentImpl extends UserDataHolderBase implements Content {
  private String myDisplayName;
  private String myDescription;
  private JComponent myComponent;
  private Icon myIcon;
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private ContentManager myManager = null;
  private boolean myIsLocked = false;
  private boolean myPinnable = true;
  private Icon myLayeredIcon = new LayeredIcon(2);
  private Disposable myDisposer = null;
  private String myTabName;
  private String myToolwindowTitle;
  private boolean myCloseable = true;
  private ActionGroup myActions;
  private String myPlace;

  private AlertIcon myAlertIcon;

  private JComponent myActionsContextComponent;
  private JComponent mySearchComponent;

  private Computable<JComponent> myFocusRequest;

  public ContentImpl(JComponent component, String displayName, boolean isPinnable) {
    myComponent = component;
    myDisplayName = displayName;
    myPinnable = isPinnable;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setComponent(JComponent component) {
    Component oldComponent = myComponent;
    myComponent = component;
    myChangeSupport.firePropertyChange(PROP_COMPONENT, oldComponent, myComponent);
  }

  public JComponent getPreferredFocusableComponent() {
    return myFocusRequest == null ? myComponent : myFocusRequest.compute();
  }

  public void setPreferredFocusableComponent(final JComponent c) {
    setPreferredFocusedComponent(new Computable<JComponent>() {
      public JComponent compute() {
        return c;
      }
    });
  }

  public void setPreferredFocusedComponent(final Computable<JComponent> computable) {
    myFocusRequest = computable;
  }

  public void setIcon(Icon icon) {
    Icon oldValue = getIcon();
    myIcon = icon;
    myLayeredIcon = LayeredIcon.create(myIcon, getPinIcon());
    myChangeSupport.firePropertyChange(PROP_ICON, oldValue, getIcon());
  }

  public Icon getIcon() {
    if (myIsLocked) {
      return myIcon == null ? getPinIcon() : myLayeredIcon;
    }
    else {
      return myIcon;
    }
  }

  private static Icon getPinIcon() {
    return IconLoader.getIcon("/nodes/pinToolWindow.png");
  }

  public void setDisplayName(String displayName) {
    String oldValue = myDisplayName;
    myDisplayName = displayName;
    myChangeSupport.firePropertyChange(PROP_DISPLAY_NAME, oldValue, myDisplayName);
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public void setTabName(String tabName) {
    myTabName = tabName;
  }

  public String getTabName() {
    if (myTabName != null) return myTabName;
    return myDisplayName;
  }

  public void setToolwindowTitle(String toolwindowTitle) {
    myToolwindowTitle = toolwindowTitle;
  }

  public String getToolwindowTitle() {
    if (myToolwindowTitle != null) return myToolwindowTitle;
    return myDisplayName;
  }

  public Disposable getDisposer() {
    return myDisposer;
  }

  public void setDisposer(Disposable disposer) {
    myDisposer = disposer;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    String oldValue = myDescription;
    myDescription = description;
    myChangeSupport.firePropertyChange(PROP_DESCRIPTION, oldValue, myDescription);
  }

  public void addPropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  public void setManager(ContentManager manager) {
    myManager = manager;
  }

  public ContentManager getManager() {
    return myManager;
  }

  public boolean isSelected() {
    return myManager != null && myManager.isSelected(this);
  }

  public final void release() {
    Disposer.dispose(this);
  }

  //TODO[anton,vova] investigate
  public boolean isValid() {
    return myManager != null;
  }

  public boolean isPinned() {
    return myIsLocked;
  }

  public void setPinned(boolean locked) {
    if (isPinnable()) {
      Icon oldIcon = getIcon();
      myIsLocked = locked;
      Icon newIcon = getIcon();
      myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, newIcon);
    }
  }

  public boolean isPinnable() {
    return myPinnable;
  }

  public boolean isCloseable() {
    return myCloseable;
  }

  public void setCloseable(final boolean closeable) {
    myCloseable = closeable;
  }

  public void setActions(final ActionGroup actions, String place, @Nullable JComponent contextComponent) {
    final ActionGroup oldActions = myActions;
    myActions = actions;
    myPlace = place;
    myActionsContextComponent = contextComponent;
    myChangeSupport.firePropertyChange(PROP_ACTIONS, oldActions, myActions);
  }

  public JComponent getActionsContextComponent() {
    return myActionsContextComponent;
  }

  public ActionGroup getActions() {
    return myActions;
  }

  public String getPlace() {
    return myPlace;
  }

  @NonNls
  public String toString() {
    return "Content name=" + myDisplayName;
  }

  public void dispose() {
    if (myComponent instanceof Disposable) {
      Disposer.dispose((Disposable)myComponent);
    }

    myComponent = null;
    myFocusRequest = null;
    myManager = null;

    clearUserData();
    if (myDisposer != null) {
      Disposer.dispose(myDisposer);
      myDisposer = null;
    }
  }

  @Nullable
  public AlertIcon getAlertIcon() {
    return myAlertIcon;
  }

  public void setAlertIcon(@Nullable final AlertIcon icon) {
    myAlertIcon = icon;
  }

  public void fireAlert() {
    myChangeSupport.firePropertyChange(PROP_ALERT, null, true);
  }

  public void setSearchComponent(@Nullable final JComponent comp) {
    mySearchComponent = comp;
  }

  public JComponent getSearchComponent() {
    return mySearchComponent;
  }
}
