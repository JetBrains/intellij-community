/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.ui;

import javax.swing.*;

/**
 * @author oleg
 * @date 9/27/11
 */
public class GitHubCreateGistPanel {
  private JTextArea myDescriptionTextArea;
  private JCheckBox myPrivateCheckBox;
  private JPanel myPanel;
  private JCheckBox myAnonymousCheckBox;
  private JCheckBox myOpenInBrowserCheckBox;

  public GitHubCreateGistPanel() {
    myDescriptionTextArea.setBorder(BorderFactory.createEtchedBorder());
  }

  public boolean isPrivate(){
    return myPrivateCheckBox.isSelected();
  }

  public boolean isAnonymous(){
    return myAnonymousCheckBox.isSelected();
  }

  public boolean isOpenInBrowser(){
    return myOpenInBrowserCheckBox.isSelected();
  }

  public void setPrivate(final boolean isPrivate){
    myPrivateCheckBox.setSelected(isPrivate);
  }

  public void setAnonymous(final boolean anonymous){
    myAnonymousCheckBox.setSelected(anonymous);
  }
  public void setOpenInBrowser(final boolean openInBrowser) {
    myOpenInBrowserCheckBox.setSelected(openInBrowser);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JTextArea getDescriptionTextArea() {
    return myDescriptionTextArea;
  }

}
