/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.accessibility;

import javax.accessibility.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Locale;

/**
 * AccessibleContext implementation that delegates all calls to another context.
 *
 * This is useful when a component needs to expose an AccessibleContext where most
 * of the implementation comes from another context while at the same a specific subset
 * of the behavior can be overridden.
 */
public class AccessibleContextDelegate extends AccessibleContext {
  private AccessibleContext myContext;

  public AccessibleContextDelegate(AccessibleContext context) {
    myContext = context;
  }

  @Override
  public void setAccessibleName(String s) {
    myContext.setAccessibleName(s);
  }

  @Override
  public void setAccessibleDescription(String s) {
    myContext.setAccessibleDescription(s);
  }

  @Override
  public void setAccessibleParent(Accessible a) {
    myContext.setAccessibleParent(a);
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myContext.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myContext.removePropertyChangeListener(listener);
  }

  @Override
  public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    myContext.firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public AccessibleRole getAccessibleRole() {
    return myContext.getAccessibleRole();
  }

  @Override
  public AccessibleStateSet getAccessibleStateSet() {
    return myContext.getAccessibleStateSet();
  }

  @Override
  public int getAccessibleIndexInParent() {
    return myContext.getAccessibleIndexInParent();
  }

  @Override
  public int getAccessibleChildrenCount() {
    return myContext.getAccessibleChildrenCount();
  }

  @Override
  public Accessible getAccessibleChild(int i) {
    return myContext.getAccessibleChild(i);
  }

  @Override
  public Locale getLocale() throws IllegalComponentStateException {
    return myContext.getLocale();
  }

  @Override
  public String getAccessibleName() {
    return myContext.getAccessibleName();
  }

  @Override
  public String getAccessibleDescription() {
    return myContext.getAccessibleDescription();
  }

  @Override
  public Accessible getAccessibleParent() {
    return myContext.getAccessibleParent();
  }

  @Override
  public AccessibleAction getAccessibleAction() {
    return myContext.getAccessibleAction();
  }

  @Override
  public AccessibleComponent getAccessibleComponent() {
    return myContext.getAccessibleComponent();
  }

  @Override
  public AccessibleSelection getAccessibleSelection() {
    return myContext.getAccessibleSelection();
  }

  @Override
  public AccessibleText getAccessibleText() {
    return myContext.getAccessibleText();
  }

  @Override
  public AccessibleEditableText getAccessibleEditableText() {
    return myContext.getAccessibleEditableText();
  }

  @Override
  public AccessibleValue getAccessibleValue() {
    return myContext.getAccessibleValue();
  }

  @Override
  public AccessibleIcon[] getAccessibleIcon() {
    return myContext.getAccessibleIcon();
  }

  @Override
  public AccessibleRelationSet getAccessibleRelationSet() {
    return myContext.getAccessibleRelationSet();
  }

  @Override
  public AccessibleTable getAccessibleTable() {
    return myContext.getAccessibleTable();
  }
}
