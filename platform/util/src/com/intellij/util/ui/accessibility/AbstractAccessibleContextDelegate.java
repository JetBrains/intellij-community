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

import org.jetbrains.annotations.NotNull;

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
public abstract class AbstractAccessibleContextDelegate extends AccessibleContext {
  /**
   * Sub-classes provide the {@link AccessibleContext} to forward calls to.
   */
  @NotNull
  protected abstract AccessibleContext getDelegate();

  @Override
  public void setAccessibleName(String s) {
    getDelegate().setAccessibleName(s);
  }

  @Override
  public void setAccessibleDescription(String s) {
    getDelegate().setAccessibleDescription(s);
  }

  @Override
  public void setAccessibleParent(Accessible a) {
    getDelegate().setAccessibleParent(a);
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    getDelegate().addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    getDelegate().removePropertyChangeListener(listener);
  }

  @Override
  public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    getDelegate().firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public AccessibleRole getAccessibleRole() {
    return getDelegate().getAccessibleRole();
  }

  @Override
  public AccessibleStateSet getAccessibleStateSet() {
    return getDelegate().getAccessibleStateSet();
  }

  @Override
  public int getAccessibleIndexInParent() {
    return getDelegate().getAccessibleIndexInParent();
  }

  @Override
  public int getAccessibleChildrenCount() {
    return getDelegate().getAccessibleChildrenCount();
  }

  @Override
  public Accessible getAccessibleChild(int i) {
    return getDelegate().getAccessibleChild(i);
  }

  @Override
  public Locale getLocale() throws IllegalComponentStateException {
    return getDelegate().getLocale();
  }

  @Override
  public String getAccessibleName() {
    return getDelegate().getAccessibleName();
  }

  @Override
  public String getAccessibleDescription() {
    return getDelegate().getAccessibleDescription();
  }

  @Override
  public Accessible getAccessibleParent() {
    return getDelegate().getAccessibleParent();
  }

  @Override
  public AccessibleAction getAccessibleAction() {
    return getDelegate().getAccessibleAction();
  }

  @Override
  public AccessibleComponent getAccessibleComponent() {
    return getDelegate().getAccessibleComponent();
  }

  @Override
  public AccessibleSelection getAccessibleSelection() {
    return getDelegate().getAccessibleSelection();
  }

  @Override
  public AccessibleText getAccessibleText() {
    return getDelegate().getAccessibleText();
  }

  @Override
  public AccessibleEditableText getAccessibleEditableText() {
    return getDelegate().getAccessibleEditableText();
  }

  @Override
  public AccessibleValue getAccessibleValue() {
    return getDelegate().getAccessibleValue();
  }

  @Override
  public AccessibleIcon[] getAccessibleIcon() {
    return getDelegate().getAccessibleIcon();
  }

  @Override
  public AccessibleRelationSet getAccessibleRelationSet() {
    return getDelegate().getAccessibleRelationSet();
  }

  @Override
  public AccessibleTable getAccessibleTable() {
    return getDelegate().getAccessibleTable();
  }
}
