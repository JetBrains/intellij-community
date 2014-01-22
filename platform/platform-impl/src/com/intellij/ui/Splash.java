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
package com.intellij.ui;

import com.intellij.ide.StartupProgress;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;


/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and find
 * section corresponding to IDE logo. It should look like:
 * <p>
 *   &lt;logo url=&quot;/idea_logo.png&quot; textcolor=&quot;919191&quot; progressColor=&quot;264db5&quot; progressY=&quot;235&quot;/&gt;
 * </p>
 * <p>where <code>url</code> is path to your splash image
 * <p><code>textColor</code> is HEX representation of text color for user name
 * <p><code>progressColor</code> is progress bar color
 * <p><code>progressY</code> is Y coordinate of the progress bar
 * <p><code>progressTailIcon</code> is a path to flame effect icon
 *
 * @author Konstantin Bulenkov
 */
public class Splash extends JDialog implements StartupProgress {
  @Nullable public static Rectangle BOUNDS;
  private final Icon myImage;
  private int myProgressHeight = 2;
  private Color myProgressColor = null;
  private int myProgressY;
  private float myProgress;
  private boolean mySplashIsVisible;
  private int myProgressLastPosition = 0;
  private final JLabel myLabel;
  private Icon myProgressTail;

  public Splash(String imageName, final Color textColor) {
    super((Frame)null, false);

    setUndecorated(true);
    setResizable(false);
    setFocusableWindowState(false);

    Icon originalImage = IconLoader.getIcon(imageName);
    myImage = new SplashImage(originalImage, textColor);
    myLabel = new JLabel(myImage) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        mySplashIsVisible = true;
        myProgressLastPosition = 0;
        paintProgress(g);
      }
    };
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.add(myLabel, BorderLayout.CENTER);
    Dimension size = getPreferredSize();
    setSize(size);
    pack();
    setLocationRelativeTo(null);
  }

  public Splash(ApplicationInfoEx info) {
    this(info.getSplashImageUrl(), info.getSplashTextColor());
    if (info instanceof ApplicationInfoImpl) {
      final ApplicationInfoImpl appInfo = (ApplicationInfoImpl)info;
      myProgressHeight = 2;
      myProgressColor = appInfo.getProgressColor();
      myProgressY = appInfo.getProgressY();
      myProgressTail = appInfo.getProgressTailIcon();
    }
  }

  @SuppressWarnings("deprecation")
  public void show() {
    super.show();
    toFront();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    BOUNDS = getBounds();
    //Sometimes this causes deadlock in EDT
    // http://bugs.sun.com/view_bug.do?bug_id=6724890
    //
    //myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
  }

  @Override
  public void showProgress(String message, float progress) {
    if (getProgressColor() == null) return;
    //myMessage = message;
    myProgress = progress;
    myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
  }

  private void paintProgress(Graphics g) {
    final Color color = getProgressColor();
    if (color == null) return;

    if (!mySplashIsVisible) {
      myImage.paintIcon(this, g, 0, 0);
      mySplashIsVisible = true;
    }

    final int progressWidth = (int)((myImage.getIconWidth() - 2) * myProgress);
    final int width = progressWidth - myProgressLastPosition;
    g.setColor(color);
    g.fillRect(1, getProgressY(), width, getProgressHeight());
    if (myProgressTail != null) {
      myProgressTail.paintIcon(this, g, width - (myProgressTail.getIconWidth()/2), getProgressY() - (myProgressTail.getIconHeight() - getProgressHeight())/2);
    }
    myProgressLastPosition = progressWidth;
  }

  private int getProgressHeight() {
    return myProgressHeight;
  }

  private Color getProgressColor() {
    return myProgressColor;
  }

  private int getProgressY() {
    return myProgressY;
  }

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, final Color textColor) {
    if (ApplicationInfoImpl.getShadowInstance().showLicenseeInfo()) {
      final LicensingFacade provider = LicensingFacade.getInstance();
      if (provider != null) {
        UIUtil.applyRenderingHints(g);
        g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.BOLD, SystemInfo.isUnix ? 10 : 11));

        g.setColor(textColor);
        final String licensedToMessage = provider.getLicensedToMessage();
        final List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();
        g.drawString(licensedToMessage, x + 21, y + height - 49);
        if (licenseRestrictionsMessages.size() > 0) {
          g.drawString(licenseRestrictionsMessages.get(0), x + 21, y + height - 33);
        }
      }
      return true;
    }
    return false;
  }

  private static final class SplashImage implements Icon {
    private final Icon myIcon;
    private final Color myTextColor;
    private boolean myRedrawing;

    public SplashImage(Icon originalIcon, Color textColor) {
      myIcon = originalIcon;
      myTextColor = textColor;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (!myRedrawing) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignore) {}
        myRedrawing = true;
      }

      myIcon.paintIcon(c, g, x, y);

      showLicenseeInfo(g, x, y, getIconHeight(), myTextColor);
    }

    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }

  //public static void main(String[] args) {
  //  final ImageIcon icon = new ImageIcon("c:\\IDEA\\ultimate\\ultimate-resources\\src\\progress_tail.png");
  //
  //  final int w = icon.getIconWidth();
  //  final int h = icon.getIconHeight();
  //  final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
  //    .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Color.TRANSLUCENT);
  //  final Graphics2D g = image.createGraphics();
  //  icon.paintIcon(null, g, 0, 0);
  //  g.dispose();
  //
  //  for (int y = 0; y < image.getHeight(); y++) {
  //    for (int x = 0; x < image.getWidth(); x++) {
  //      final Color c = new Color(image.getRGB(x, y), true);
  //      System.out.print(String.format("[%3d,%3d,%3d,%3d]  ", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
  //    }
  //    System.out.println("");
  //  }
  //}
}
