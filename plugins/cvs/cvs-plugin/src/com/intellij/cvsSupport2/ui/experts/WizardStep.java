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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.border.IdeaTitledBorder;

import javax.swing.*;

/**
 * author: lesya
 */
public abstract class WizardStep extends StepAdapter{
  private JComponent myComponent;
  private final CvsWizard myWizard;
  private final String myDescription;

  protected WizardStep(String description, CvsWizard wizard) {
    myWizard = wizard;
    myDescription = description;
  }

  protected void init(){}

  public boolean preNextCheck() {
    return true;
  }

  public abstract boolean nextIsEnabled();
  public void activate() {}
  protected abstract JComponent createComponent();
  protected void dispose() {}

  @Override
  public JComponent getComponent(){
    if (myComponent == null) {
      myComponent = createComponent();
      final IdeaTitledBorder border = IdeBorderFactory.createTitledBorder(myDescription, false);
      myComponent.setBorder(border);
    }
    return myComponent;
  }

  protected void setStepTitle(String title){
    final IdeaTitledBorder border = IdeBorderFactory.createTitledBorder(title, false);
    getComponent().setBorder(border);
  }

  protected CvsWizard getWizard() {
    return myWizard;
  }

  public void saveState(){}

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComponent;
  }

  public void focus() {
    SwingUtilities.invokeLater(() -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getPreferredFocusedComponent(), true)));
  }
}
