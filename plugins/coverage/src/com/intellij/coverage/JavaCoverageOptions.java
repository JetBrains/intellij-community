// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;

public class JavaCoverageOptions extends CoverageOptions {

  private final JavaCoverageOptionsProvider myCoverageOptionsProvider;
  private JavaCoverageOptionsEditor myEditor;

  public JavaCoverageOptions(JavaCoverageOptionsProvider coverageOptionsProvider) {
    myCoverageOptionsProvider = coverageOptionsProvider;
  }

  @Override
  public JComponent createComponent() {
    myEditor = new JavaCoverageOptionsEditor();
    return myEditor.getComponent();
  }

  @Override
  public boolean isModified() {
    return myEditor.isModified(myCoverageOptionsProvider);
  }

  @Override
  public void apply() {
    myEditor.apply(myCoverageOptionsProvider);
  }

  @Override
  public void reset() {
    myEditor.reset(myCoverageOptionsProvider);
  }

  @Override
  public void disposeUIResources() {
    myEditor = null;
  }
  
  private static class JavaCoverageOptionsEditor {

    private final JPanel myPanel = new JPanel(new VerticalFlowLayout());
    private final JCheckBox myImplicitCheckBox = new JCheckBox("Ignore implicitly declared default constructors", true);
    private final JCheckBox myEmptyCheckBox = new JCheckBox("Ignore empty private constructors of utility classes", true);

    JavaCoverageOptionsEditor() {
      myPanel.setBorder(IdeBorderFactory.createTitledBorder("Java coverage"));
      myPanel.add(myImplicitCheckBox);
      myPanel.add(myEmptyCheckBox);
    }

    public JPanel getComponent() {
      return myPanel;
    }

    public boolean isModified(JavaCoverageOptionsProvider provider) {
      return myImplicitCheckBox.isSelected() != provider.ignoreImplicitConstructors() ||
             myEmptyCheckBox.isSelected() != provider.ignoreEmptyPrivateConstructors();
    }

    public void apply(JavaCoverageOptionsProvider provider) {
      provider.setIgnoreImplicitConstructors(myImplicitCheckBox.isSelected());
      provider.setIgnoreEmptyPrivateConstructors(myEmptyCheckBox.isSelected());
    }

    public void reset(JavaCoverageOptionsProvider provider) {
      myImplicitCheckBox.setSelected(provider.ignoreImplicitConstructors());
      myEmptyCheckBox.setSelected(provider.ignoreEmptyPrivateConstructors());
    }
  }
}
