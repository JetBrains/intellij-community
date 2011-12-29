/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.exportSignedPackage;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class CheckModulePanel extends JPanel {
  private boolean myHasError;
  private boolean myHasWarnings;

  public CheckModulePanel() {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP));
  }

  public void updateMessages(AndroidFacet facet) {
    clearMessages();
    revalidate();
  }

  public boolean hasError() {
    return myHasError;
  }

  public boolean hasWarnings() {
    return myHasWarnings;
  }

  public void clearMessages() {
    removeAll();
    myHasError = false;
    myHasWarnings = false;
  }

  public void addError(String message) {
    JLabel label = new JLabel();
    label.setIcon(Messages.getErrorIcon());
    label.setText("<html><body><b>Error: " + message + "</b></body></html>");
    add(label);
    myHasError = true;
  }

  public void addWarning(String message) {
    JLabel label = new JLabel();
    label.setIcon(Messages.getWarningIcon());
    label.setText("<html><body><b>Warning: " + message + "</b></body></html>");
    add(label);
    myHasWarnings = true;
  }
}
