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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.ide.wizard.StepAdapter;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public abstract class WizardStep extends StepAdapter{
  private JComponent myComponent;
  private JPanel myStepPresentation;
  private final CvsWizard myWizard;
  private final String myDescription;
  private JLabel myTitle;

  protected WizardStep(String description, CvsWizard wizard) {
    myWizard = wizard;
    myDescription = description;
  }

  protected void init(){
    myComponent = createComponent();
    myStepPresentation = new JPanel();
    myStepPresentation.setLayout(new GridBagLayout());
    myStepPresentation.add(myComponent, new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.CENTER,
                                                               GridBagConstraints.BOTH, new Insets (3, 3, 3, 3), 0, 0));
    myTitle = new JLabel(myDescription);
    myTitle.setForeground(new Color(0,86,159));
    Font font= myTitle.getFont();
    myTitle.setFont(new Font(font.getName(),Font.BOLD, 12 ));
    myStepPresentation.add(myTitle, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                               GridBagConstraints.NONE, new Insets (3, 3, 3, 3), 0, 0));

    myStepPresentation.add(new JSeparator(JSeparator.HORIZONTAL), new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.WEST,
                                                                 GridBagConstraints.HORIZONTAL, new Insets (0, 0, 0, 0), 0, 0));

  }

  public boolean preNextCheck() {
    return true;
  }

  public abstract boolean nextIsEnabled();
  public abstract boolean setActive();
  protected abstract JComponent createComponent();
  protected abstract void dispose();

  public JComponent getComponent(){
    return myStepPresentation;
  }

  protected JComponent getStepComponent(){
    return myComponent;
  }

  public Icon getIcon() {
    return null;
  }

  protected void setStepTitle(String title){
    myTitle.setText(title);
  }

  protected CvsWizard getWizard() {
    return myWizard;
  }

  public void saveState(){

  }

  public Component getPreferredFocusedComponent() {
    return myComponent;
  }

}
