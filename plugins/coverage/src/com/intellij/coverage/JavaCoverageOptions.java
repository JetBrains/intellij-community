/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  public JComponent getComponent() {
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

    private JPanel myPanel = new JPanel(new VerticalFlowLayout());
    private JCheckBox myImplicitCheckBox = new JCheckBox("Ignore implicitly declared default constructors", true);
    private JCheckBox myEmptyCheckBox = new JCheckBox("Ignore empty private constructors of utility classes", true);

    public JavaCoverageOptionsEditor() {
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
