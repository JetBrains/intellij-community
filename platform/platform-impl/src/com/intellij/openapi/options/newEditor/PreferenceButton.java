/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

/**
 * @author Konstantin Bulenkov
 */
public class PreferenceButton extends JPanel {
  private final JLabel myIcon;
  private final JLabel myLabel;

  public PreferenceButton(String label, Icon icon) {
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    myIcon = new JLabel(icon);
    myLabel = new JLabel(label);
    myIcon.setHorizontalAlignment(SwingConstants.CENTER);
    myIcon.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    //add(myIcon);
    //add(myLabel);
    setPreferredSize(new Dimension(64, 64));
    setSize(64, 64);
    setBackground(new Color((int)new Random().nextInt(255 * 255 * 255)));
  }
}
