/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui.content.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.AlertIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ContentImpl extends UserDataHolderBase implements Content {
  private static Icon ourEmptyPinIcon;

  private String myDisplayName;
  private String myDescription;
  private JComponent myComponent;
  private Icon myIcon;
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private ContentManager myManager;
  private boolean myIsLocked;
  private boolean myPinnable = true;
  private Icon myLayeredIcon = new LayeredIcon(2);
  private Disposable myDisposer;
  private boolean myShouldDisposeContent = true;
  private String myTabName;
  private String myToolwindowTitle;
  private boolean myCloseable = true;
  private ActionGroup myActions;
  private String myPlace;

  private AlertIcon myAlertIcon;

  private JComponent myActionsContextComponent;
  private JComponent mySearchComponent;

  private Computable<JComponent> myFocusRequest;
  private BusyObject myBusyObject;
  private String mySeparator;
  private Icon myPopupIcon;
  private long myExecutionId;
  private String myHelpId;

  public ContentImpl(JComponent component, String displayName, boolean isPinnable) {
    myComponent = component;
    myDisplayName = displayName;
    myPinnable = isPinnable;
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void setComponent(JComponent component) {
    Component oldComponent = myComponent;
    myComponent = component;
    myChangeSupport.firePropertyChange(PROP_COMPONENT, oldComponent, myComponent);
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myFocusRequest == null ? myComponent : myFocusRequest.compute();
  }

  @Override
  public void setPreferredFocusableComponent(final JComponent c) {
    setPreferredFocusedComponent(() -> c);
  }

  @Override
  public void setPreferredFocusedComponent(final Computable<JComponent> computable) {
    myFocusRequest = computable;
  }

  @Override
  public void setIcon(Icon icon) {
    Icon oldValue = getIcon();
    myIcon = icon;
    myLayeredIcon = LayeredIcon.create(myIcon, AllIcons.Nodes.PinToolWindow);
    myChangeSupport.firePropertyChange(PROP_ICON, oldValue, getIcon());
  }

  @Override
  public Icon getIcon() {
    if (myIsLocked) {
      return myIcon == null ? getEmptyPinIcon() : myLayeredIcon;
    }
    else {
      return myIcon;
    }
  }

  @NotNull
  private static Icon getEmptyPinIcon() {
    if (ourEmptyPinIcon == null) {
      Icon icon = AllIcons.Nodes.PinToolWindow;
      int width = icon.getIconWidth();
      ourEmptyPinIcon = IconUtil.cropIcon(icon, new Rectangle(width / 2, 0, width - width / 2, icon.getIconHeight()));
    }
    return ourEmptyPinIcon;
  }

  @Override
  public void setDisplayName(String displayName) {
    String oldValue = myDisplayName;
    myDisplayName = displayName;
    myChangeSupport.firePropertyChange(PROP_DISPLAY_NAME, oldValue, myDisplayName);
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public void setTabName(String tabName) {
    myTabName = tabName;
  }

  @Override
  public String getTabName() {
    if (myTabName != null) return myTabName;
    return myDisplayName;
  }

  @Override
  public void setToolwindowTitle(String toolwindowTitle) {
    myToolwindowTitle = toolwindowTitle;
  }

  @Override
  public String getToolwindowTitle() {
    if (myToolwindowTitle != null) return myToolwindowTitle;
    return myDisplayName;
  }

  @Override
  public Disposable getDisposer() {
    return myDisposer;
  }

  @Override
  public void setDisposer(Disposable disposer) {
    myDisposer = disposer;
  }

  @Override
  public void setShouldDisposeContent(boolean value) {
    myShouldDisposeContent = value;
  }

  @Override
  public boolean shouldDisposeContent() {
    return myShouldDisposeContent;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public void setDescription(String description) {
    String oldValue = myDescription;
    myDescription = description;
    myChangeSupport.firePropertyChange(PROP_DESCRIPTION, oldValue, myDescription);
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  public void setManager(ContentManager manager) {
    myManager = manager;
  }

  @Override
  public ContentManager getManager() {
    return myManager;
  }

  @Override
  public boolean isSelected() {
    return myManager != null && myManager.isSelected(this);
  }

  @Override
  public final void release() {
    Disposer.dispose(this);
  }

  //TODO[anton,vova] investigate
  @Override
  public boolean isValid() {
    return myManager != null;
  }

  @Override
  public boolean isPinned() {
    return myIsLocked;
  }

  @Override
  public void setPinned(boolean locked) {
    if (isPinnable()) {
      Icon oldIcon = getIcon();
      myIsLocked = locked;
      Icon newIcon = getIcon();
      myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, newIcon);
    }
  }

  @Override
  public boolean isPinnable() {
    return myPinnable;
  }

  @Override
  public void setPinnable(boolean pinnable) {
    myPinnable = pinnable;
  }

  @Override
  public boolean isCloseable() {
    return myCloseable;
  }

  @Override
  public void setCloseable(final boolean closeable) {
    myCloseable = closeable;
  }

  @Override
  public void setActions(final ActionGroup actions, String place, @Nullable JComponent contextComponent) {
    final ActionGroup oldActions = myActions;
    myActions = actions;
    myPlace = place;
    myActionsContextComponent = contextComponent;
    myChangeSupport.firePropertyChange(PROP_ACTIONS, oldActions, myActions);
  }

  @Override
  public JComponent getActionsContextComponent() {
    return myActionsContextComponent;
  }

  @Override
  public ActionGroup getActions() {
    return myActions;
  }

  @Override
  public String getPlace() {
    return myPlace;
  }

  @Override
  @NonNls
  public String toString() {
    StringBuilder sb = new StringBuilder("Content name=").append(myDisplayName);
    if (myIsLocked)
      sb.append(", pinned");
    if (myExecutionId != 0)
      sb.append(", executionId=").append(myExecutionId);
    return sb.toString();
  }

  @Override
  public void dispose() {
    if (myShouldDisposeContent && myComponent instanceof Disposable) {
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

  @Override
  @Nullable
  public AlertIcon getAlertIcon() {
    return myAlertIcon;
  }

  @Override
  public void setAlertIcon(@Nullable final AlertIcon icon) {
    myAlertIcon = icon;
  }

  @Override
  public void fireAlert() {
    myChangeSupport.firePropertyChange(PROP_ALERT, null, true);
  }

  @Override
  public void setBusyObject(BusyObject object) {
    myBusyObject = object;
  }

  @Override
  public String getSeparator() {
    return mySeparator;
  }

  @Override
  public void setSeparator(String separator) {
    mySeparator = separator;
  }

  @Override
  public void setPopupIcon(Icon icon) {
    myPopupIcon = icon;
  }

  @Override
  public Icon getPopupIcon() {
    return myPopupIcon != null ? myPopupIcon : getIcon();
  }

  @Override
  public BusyObject getBusyObject() {
    return myBusyObject;
  }

  @Override
  public void setSearchComponent(@Nullable final JComponent comp) {
    mySearchComponent = comp;
  }

  @Override
  public JComponent getSearchComponent() {
    return mySearchComponent;
  }

  @Override
  public void setExecutionId(long executionId) {
    myExecutionId = executionId;
  }

  @Override
  public long getExecutionId() {
    return myExecutionId;
  }

  @Override
  public void setHelpId(String helpId) {
    myHelpId = helpId;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return myHelpId;
  }
}
