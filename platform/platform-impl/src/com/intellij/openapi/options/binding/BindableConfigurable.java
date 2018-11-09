// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.options.binding;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class BindableConfigurable implements UnnamedConfigurable {

  protected ControlBinder myBinder;

  protected BindableConfigurable(ControlBinder binder) {
    myBinder = binder;
  }

  protected BindableConfigurable() {

  }

  protected void bindAnnotations() {
    getBinder().bindAnnotations(this);
  }

  protected void bindControl(JComponent control, String propertyName, boolean instant) {
    getBinder().bindControl(control, propertyName, instant);
  }

  protected void bindControl(ControlValueAccessor controlAccessor, String propertyName, boolean instant) {
    getBinder().bindControl(controlAccessor, propertyName, instant);
  }

  @Override
  public boolean isModified() {
    return getBinder().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    getBinder().apply();
  }

  @Override
  public void reset() {
    getBinder().reset();
  }

  protected ControlBinder getBinder() {
    return myBinder;
  }
}
