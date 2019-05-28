// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.StartupProgress;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 *
 * @author Konstantin Bulenkov
 */
public class Splash extends JDialog implements StartupProgress {
  private static final float JBUI_INIT_SCALE = JBUI.scale(1f);

  private final ApplicationInfoEx myInfo;
  private int myProgressHeight;
  private Color myProgressColor;
  private int myProgressY;
  private double myProgress;
  private int myProgressLastPosition = 0;
  private Icon myProgressTail;
  private final List<ProgressSlide> myProgressSlideImages = new ArrayList<>();
  private final Icon myIcon;

  private final NotNullLazyValue<Font> myFont = createFont();

  public Splash(@NotNull ApplicationInfoEx info) {
    super((Frame)null, false);

    myInfo = info;
    if (info instanceof ApplicationInfoImpl) {
      final ApplicationInfoImpl appInfo = (ApplicationInfoImpl)info;
      myProgressHeight = appInfo.getProgressHeight();
      myProgressColor = appInfo.getProgressColor();
      myProgressY = appInfo.getProgressY();
      myProgressTail = appInfo.getProgressTailIcon();
    }
    setUndecorated(true);
    if (!SystemInfo.isLinux) {
      setResizable(false);
    }
    setFocusableWindowState(false);

    myIcon = IconLoader.getIconSnapshot(IconLoader.getIcon(info.getSplashImageUrl(), Splash.class));
    Dimension size = new Dimension(myIcon.getIconWidth(), myIcon.getIconHeight());
    if (Registry.is("suppress.focus.stealing") && Registry.is("suppress.focus.stealing.auto.request.focus")) {
      setAutoRequestFocus(false);
    }
    setSize(size);
    setLocationInTheCenterOfScreen();

    initImages();

    setVisible(true);
    toFront();
  }

  @NotNull
  public static NotNullLazyValue<Font> createFont() {
    return NotNullLazyValue.createValue(() -> {
      Font font;
      if (SystemInfo.isMacOSElCapitan) {
        font = createFont(".SF NS Text");
      }
      else {
        //noinspection SpellCheckingInspection
        font = SystemInfo.isMacOSYosemite ? createFont("HelveticaNeue-Regular") : null;
      }

      if (font == null || UIUtil.isDialogFont(font)) {
        font = createFont(UIUtil.ARIAL_FONT_NAME);
      }
      return font;
    });
  }

  @Override
  public void paint(Graphics g) {
    myIcon.paintIcon(this, g, 0, 0);
    showLicenseeInfo(g, 0, 0, myIcon.getIconHeight(), myInfo, myFont);
    paintProgress(g);
  }

  private void initImages() {
    List<ProgressSlide> progressSlides = myInfo.getProgressSlides();
    if (progressSlides.isEmpty()) {
      return;
    }

    for (ProgressSlide progressSlide : progressSlides) {
      String url = progressSlide.getUrl();
      Icon icon = IconLoader.getIcon(url);
      progressSlide.setImageIcon(icon);
      myProgressSlideImages.add(progressSlide);
    }

    myProgressSlideImages.sort(Comparator.comparing(ProgressSlide::getProgressRation));
  }

  private void setLocationInTheCenterOfScreen() {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    if (SystemInfo.isWindows) {
      JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(getGraphicsConfiguration()));
    }
    setLocation(UIUtil.getCenterPoint(bounds, getSize()));
  }

  @Override
  public void showProgress(double progress) {
    if (myProgressColor == null) return;
    if (((progress - myProgress) > 0.01) || (progress > 0.99)) {
      myProgress = progress;
      paintProgress(getGraphics());
    }
  }

  @Override
  public void dispose() {
    super.dispose();

    DialogWrapper.cleanupRootPane(rootPane);
    rootPane = null;
  }

  private void paintProgress(@NotNull Graphics g) {
    boolean hasSlides = !myProgressSlideImages.isEmpty();
    if (hasSlides) {
      paintSlides(g);
    }

    Color color = myProgressColor;
    if (color == null) {
      return;
    }

    final int progressWidth = (int)(myIcon.getIconWidth() * myProgress);
    int currentWidth = progressWidth - myProgressLastPosition;
    if (currentWidth == 0) {
      return;
    }

    g.setColor(color);
    int y = hasSlides ? myIcon.getIconHeight() - getProgressHeight() : getProgressY();
    g.fillRect(myProgressLastPosition, y, currentWidth, getProgressHeight());
    if (myProgressTail != null) {
      float onePixel = JBUI_INIT_SCALE;
      myProgressTail.paintIcon(this, g, (int)(currentWidth - (myProgressTail.getIconWidth() / onePixel / 2f * onePixel)),
                               (int)(getProgressY() - (myProgressTail.getIconHeight() - getProgressHeight()) / onePixel / 2f * onePixel)); //I'll buy you a beer if you understand this line without playing with it
    }
    myProgressLastPosition = progressWidth;
  }

  private void paintSlides(Graphics g) {
    for (ProgressSlide progressSlide : myProgressSlideImages) {
      if (progressSlide.getProgressRation() <= myProgress) {
        progressSlide.getLoadedImage().paintIcon(this, g, 0, 0);
      }
    }
  }

  private int getProgressHeight() {
    return uiScale(myProgressHeight);
  }

  private int getProgressY() {
    return uiScale(myProgressY);
  }

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, @NotNull ApplicationInfoEx info, @NotNull NotNullLazyValue<? extends Font> font) {
    if (!info.showLicenseeInfo()) {
      return false;
    }

    LicensingFacade provider = LicensingFacade.getInstance();
    if (provider == null) {
      return true;
    }

    String licensedToMessage = provider.getLicensedToMessage();
    List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();
    if (licensedToMessage == null && licenseRestrictionsMessages.isEmpty()) {
      return true;
    }

    UIUtil.applyRenderingHints(g);
    g.setFont(font.getValue());
    g.setColor(info.getSplashTextColor());

    int offsetX = Math.max(uiScale(15), uiScale(((ApplicationInfoImpl)info).getLicenseOffsetX()));
    int offsetY = ((ApplicationInfoImpl)info).getLicenseOffsetY();

    if (licensedToMessage != null) {
      g.drawString(licensedToMessage, x + offsetX, y + height - uiScale(offsetY));
    }

    if (!licenseRestrictionsMessages.isEmpty()) {
      g.drawString(licenseRestrictionsMessages.get(0), x + offsetX, y + height - uiScale(offsetY - 16));
    }
    return true;
  }

  @NotNull
  protected static Font createFont(String name) {
    return new Font(name, Font.PLAIN, uiScale(12));
  }

  private static int uiScale(int i) {
    return (int)(i * JBUI_INIT_SCALE);
  }
}
