/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui;

import com.intellij.ide.StartupProgress;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;


/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 *
 * @author Konstantin Bulenkov
 */
public class Splash extends JDialog implements StartupProgress {
  @Nullable public static Rectangle BOUNDS;

  private final Icon myImage;
  private final ApplicationInfoEx myInfo;
  private int myProgressHeight;
  private Color myProgressColor = null;
  private int myProgressX;
  private int myProgressY;
  private float myProgress;
  private boolean mySplashIsVisible;
  private int myProgressLastPosition = 0;
  private final JLabel myLabel;
  private Icon myProgressTail;

  public Splash(@NotNull ApplicationInfoEx info) {
    super((Frame)null, false);

    myInfo = info;
    if (info instanceof ApplicationInfoImpl) {
      final ApplicationInfoImpl appInfo = (ApplicationInfoImpl)info;
      myProgressHeight = appInfo.getProgressHeight();
      myProgressColor = appInfo.getProgressColor();
      myProgressX = appInfo.getProgressX();
      myProgressY = appInfo.getProgressY();
      myProgressTail = appInfo.getProgressTailIcon();
    }
    setUndecorated(true);
    if (!(SystemInfo.isLinux && SystemInfo.isJavaVersionAtLeast("1.7"))) {
      setResizable(false);
    }
    setFocusableWindowState(false);

    Icon originalImage = IconLoader.getIcon(info.getSplashImageUrl());
    myImage = new SplashImage(IconLoader.getIconSnapshot(originalImage), info.getSplashTextColor());
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
    if (Registry.is("suppress.focus.stealing")) {
      setAutoRequestFocus(false);
    }
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
    if (progress - myProgress > 0.01) {
      myProgress = progress;
      myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    removeAll();
    DialogWrapper.cleanupRootPane(rootPane);
    rootPane = null;
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
      myProgressTail.paintIcon(this, g, (int)(getProgressX() + width - (myProgressTail.getIconWidth() / uiScale(1f) / 2f * uiScale(1f))),
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

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, final Color textColor, ApplicationInfo info) {
    if (ApplicationInfoImpl.getShadowInstance().showLicenseeInfo()) {
      final LicensingFacade provider = LicensingFacade.getInstance();
      if (provider != null) {
        UIUtil.applyRenderingHints(g);
        final String licensedToMessage = provider.getLicensedToMessage();
        final List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();

        Font font = SystemInfo.isMacOSElCapitan ? createFont(".SF NS Text") :
                    SystemInfo.isMacOSYosemite ? createFont("HelveticaNeue-Regular") :
                    null;
        if (font == null || UIUtil.isDialogFont(font)) {
          font = createFont(UIUtil.ARIAL_FONT_NAME);
        }

        g.setFont(UIUtil.getFontWithFallback(font));
        g.setColor(textColor);
        int offsetX = uiScale(15);
        int offsetY = 30;
        if (Registry.is("ide.new.about")) {
          if (info instanceof ApplicationInfoImpl) {
            offsetX = Math.max(offsetX, uiScale(((ApplicationInfoImpl)info).getProgressX()));
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

  @NotNull
  protected static Font createFont(String name) {
    return new Font(name, Font.PLAIN, uiScale(Registry.is("ide.new.about") ? 12 : SystemInfo.isUnix ? 10 : 11));
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

      showLicenseeInfo(g, x, y, getIconHeight(), myTextColor, myInfo);
    }

    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }
}
