// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.mock;

import com.intellij.facet.ui.FacetEditorTab;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MockFacetEditorTab extends FacetEditorTab {
  private String myDataTextField = "";
  private final MockFacetConfiguration myConfiguration;

  public MockFacetEditorTab(final MockFacetConfiguration configuration) {
    myConfiguration = configuration;
  }

  @Override
  public @Nls String getDisplayName() {
    return "";
  }

  @Override
  public @NotNull JComponent createComponent() {
    return new JPanel();
  }

  @Override
  public boolean isModified() {
    return !myDataTextField.equals(myConfiguration.getData());
  }

  public String getDataTextField() {
    return myDataTextField;
  }

  public void setDataTextField(final String dataTextField) {
    myDataTextField = dataTextField;
  }

  @Override
  public void apply() {
    myConfiguration.setData(myDataTextField);
  }

  @Override
  public void reset() {
    myDataTextField = myConfiguration.getData();
  }

  @Override
  public void disposeUIResources() {
    myDataTextField = null;
  }
}
