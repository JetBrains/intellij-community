/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
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
  private int myProgressHeight;
  private Color myProgressColor = null;
  private int myProgressX;
  private int myProgressY;
  private float myProgress;
  private boolean mySplashIsVisible;
  private int myProgressLastPosition = 0;
  private final JLabel myLabel;
  private Icon myProgressTail;

  public Splash(String imageName, final Color textColor) {
    super((Frame)null, false);

    setUndecorated(true);
    if (!(SystemInfo.isLinux && SystemInfo.isJavaVersionAtLeast("1.7"))) {
      setResizable(false);
    }
    setFocusableWindowState(false);

    Icon originalImage = IconLoader.getIcon(imageName);
    myImage = new SplashImage(IconLoader.getIconSnapshot(originalImage), textColor);
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
    setLocationInTheCenterOfScreen();
  }

  private void setLocationInTheCenterOfScreen() {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    if (SystemInfo.isWindows) {
      JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(getGraphicsConfiguration()));
    }
    setLocation(UIUtil.getCenterPoint(bounds, getSize()));
  }

  public Splash(ApplicationInfoEx info) {
    this(info.getSplashImageUrl(), info.getSplashTextColor());
    if (info instanceof ApplicationInfoImpl) {
      final ApplicationInfoImpl appInfo = (ApplicationInfoImpl)info;
      myProgressHeight = appInfo.getProgressHeight();
      myProgressColor = appInfo.getProgressColor();
      myProgressX = appInfo.getProgressX();
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

    int totalWidth = myImage.getIconWidth() - getProgressX();
    if (Registry.is("ide.new.about")) {
      totalWidth +=3;
    }
    final int progressWidth = (int)(totalWidth * myProgress);
    final int width = progressWidth - myProgressLastPosition;
    g.setColor(color);
    g.fillRect(getProgressX(), getProgressY(), width, getProgressHeight());
    if (myProgressTail != null) {
      myProgressTail.paintIcon(this, g, (int)(width - (myProgressTail.getIconWidth() / uiScale(1f) / 2f * uiScale(1f))),
                               (int)(getProgressY() - (myProgressTail.getIconHeight() - getProgressHeight()) / uiScale(1f) / 2f * uiScale(1f))); //I'll buy you a beer if you understand this line without playing with it
    }
    myProgressLastPosition = progressWidth;
  }

  private Color getProgressColor() {
    return myProgressColor;
  }

  private int getProgressHeight() {
    return (int)uiScale((float)myProgressHeight);
  }

  private int getProgressX() {
    return (int)uiScale((float)myProgressX);
  }

  private int getProgressY() {
    return (int)uiScale((float)myProgressY);
  }

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, final Color textColor) {
    if (ApplicationInfoImpl.getShadowInstance().showLicenseeInfo()) {
      final LicensingFacade provider = LicensingFacade.getInstance();
      if (provider != null) {
        UIUtil.applyRenderingHints(g);
        g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.BOLD, uiScale(Registry.is("ide.new.about") ? 12 : SystemInfo.isUnix ? 10 : 11)));

        g.setColor(textColor);
        final String licensedToMessage = provider.getLicensedToMessage();
        final List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();
        int offsetX = uiScale(15);
        int offsetY = 30;
        if (Registry.is("ide.new.about")) {
          ApplicationInfo info = getAppInfo();
          if (info instanceof ApplicationInfoImpl) {
            offsetX = Math.max(offsetX, ((ApplicationInfoImpl)info).getProgressX());
            offsetY = ((ApplicationInfoImpl)info).getLicenseOffsetY();
          } else {
            return false;
          }
        }

        g.drawString(licensedToMessage, x + offsetX, y + height - uiScale(offsetY));
        if (licenseRestrictionsMessages.size() > 0) {
          g.drawString(licenseRestrictionsMessages.get(0), x + offsetX, y + height - uiScale(offsetY - 16));
        }
      }
      return true;
    }
    return false;
  }
  private static ApplicationInfo getAppInfo() {
    return ApplicationManager.getApplication() == null ? ApplicationInfoImpl.getShadowInstance() : ApplicationInfo.getInstance();
  }

  private static float JBUI_INIT_SCALE = JBUI.scale(1f);
  private static float uiScale(float f) { return f * JBUI_INIT_SCALE; }
  private static int uiScale(int i) { return (int)(i * JBUI_INIT_SCALE); }

  private final class SplashImage implements Icon {
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
