// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry.ui;

import com.intellij.openapi.ui.CheckBoxWithDescription;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RegistryCheckBox extends CheckBoxWithDescription {
  private final RegistryValue myValue;

  public RegistryCheckBox(RegistryValue value) {
    this(value, value.getDescription(), null);
  }

  public RegistryCheckBox(RegistryValue value, String text, @Nullable String longDescription) {
    super(new JCheckBox(text), longDescription);

    myValue = value;
    getCheckBox().setSelected(myValue.asBoolean());
  }

  public boolean isChanged() {
    return getCheckBox().isSelected() != myValue.asBoolean();
  }

  public void save() {
    myValue.setValue(Boolean.valueOf(getCheckBox().isSelected()).toString());
  }
}