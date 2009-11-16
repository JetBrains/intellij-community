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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.progress.util.ColorProgressBar;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class TestStatusLine extends JPanel {
  protected final ColorProgressBar myProgressBar = new ColorProgressBar();
  protected final JLabel myState = new JLabel(ExecutionBundle.message("junit.runing.info.starting.label"));

  public TestStatusLine() {
    super(new GridLayout(1, 2));
    add(myState);
    final JPanel progressPanel = new JPanel(new GridBagLayout());
    add(progressPanel);
    progressPanel.add(myProgressBar, new GridBagConstraints(0, 0, 0, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    myProgressBar.setColor(ColorProgressBar.GREEN);
  }

  public void setStatusColor(Color color) {
    myProgressBar.setColor(color);
  }

  public Color getStatusColor() {
    return myProgressBar.getColor();
  }

  public void setFraction(double v) {
    myProgressBar.setFraction(v);
  }

  public void setText(String progressStatus_text) {
    myState.setText(progressStatus_text);
  }
}
