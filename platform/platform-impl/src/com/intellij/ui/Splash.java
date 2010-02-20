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
package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class Splash extends JDialog {
  private final Icon myImage;
  private final JLabel myLabel;

  public Splash(String imageName, final Color textColor) {
    setUndecorated(true);
    setResizable(false);
    setModal(false);
    setFocusableWindowState(false);

    Icon originalImage = IconLoader.getIcon(imageName);
    myImage = new MyIcon(originalImage, textColor);
    myLabel = new JLabel(myImage);
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.add(myLabel, BorderLayout.CENTER);
    Dimension size = getPreferredSize();
    setSize(size);
    pack();
    setLocationRelativeTo(null);
  }

  public void show() {
    super.show();
    toFront();
    myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
  }

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, final Color textColor) {
    if (ApplicationInfoImpl.getShadowInstance().showLicenseeInfo()) {
      g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.BOLD, 11));
      g.setColor(textColor);
      LicenseeInfoProvider provider = LicenseeInfoProvider.getInstance();
      if (provider != null) {
        g.drawString(provider.getLicensedToMessage(), x + 20, y + height - 52);
        g.drawString(provider.getLicenseRestrictionsMessage(), x + 20, y + height - 32);
      }
      return true;
    }
    return false;
  }

  private static final class MyIcon implements Icon {
    private final Icon myOriginalIcon;
    private final Color myTextColor;

    public MyIcon(Icon originalIcon, Color textColor) {
      myOriginalIcon = originalIcon;
      myTextColor = textColor;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      yeild();
      myOriginalIcon.paintIcon(c, g, x, y);

      showLicenseeInfo(g, x, y, getIconHeight(), myTextColor);
    }

    private static void yeild() {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
      }
    }

    public int getIconWidth() {
      return myOriginalIcon.getIconWidth();
    }

    public int getIconHeight() {
      return myOriginalIcon.getIconHeight();
    }
  }
}
