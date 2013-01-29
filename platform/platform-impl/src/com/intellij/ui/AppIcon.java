/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ui.UIUtil;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.Sanselan;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

public abstract class AppIcon {

  private static final Logger LOG = Logger.getInstance("AppIcon");

  static AppIcon ourMacImpl;
  static AppIcon ourWin7Impl;
  static AppIcon ourEmptyImpl;

  public abstract boolean setProgress(Project project, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

  public abstract boolean hideProgress(Project project, Object processId);

  public abstract void setErrorBadge(Project project, String text);

  public abstract void setOkBadge(Project project, boolean visible);

  public abstract void requestAttention(Project project, boolean critical);

  public abstract void requestFocus(IdeFrame frame);

  public static AppIcon getInstance() {
    if (ourMacImpl == null) {
      ourMacImpl = new MacAppIcon();
      ourWin7Impl = new Win7AppIcon();
      ourEmptyImpl = new EmptyIcon();
    }

    if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.6")) {
      return ourMacImpl;
    }
    else if (SystemInfo.isWin7OrNewer) {
      return ourWin7Impl;
    }
    else {
      return ourEmptyImpl;
    }
  }

  private static abstract class BaseIcon extends AppIcon {

    private ApplicationActivationListener myAppListener;
    protected Object myCurrentProcessId;
    protected double myLastValue;

    @Override
    public final boolean setProgress(Project project, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      if (!isAppActive() && Registry.is("ide.appIcon.progress") && (myCurrentProcessId == null || myCurrentProcessId.equals(processId))) {
        return _setProgress(getIdeFrame(project), processId, scheme, value, isOk);
      }
      else {
        return false;
      }
    }

    @Override
    public final boolean hideProgress(Project project, Object processId) {
      if (Registry.is("ide.appIcon.progress")) {
        return _hideProgress(getIdeFrame(project), processId);
      }
      else {
        return false;
      }
    }

    @Override
    public final void setErrorBadge(Project project, String text) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setOkBadge(getIdeFrame(project), false);
        _setTextBadge(getIdeFrame(project), text);
      }
    }

    @Override
    public final void setOkBadge(Project project, boolean visible) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setTextBadge(getIdeFrame(project), null);
        _setOkBadge(getIdeFrame(project), visible);
      }
    }

    @Override
    public final void requestAttention(Project project, boolean critical) {
      if (!isAppActive() && Registry.is("ide.appIcon.requestAttention")) {
        _requestAttention(getIdeFrame(project), critical);
      }
    }

    public abstract boolean _setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

    public abstract boolean _hideProgress(IdeFrame frame, Object processId);

    public abstract void _setTextBadge(IdeFrame frame, String text);

    public abstract void _setOkBadge(IdeFrame frame, boolean visible);

    public abstract void _requestAttention(IdeFrame frame, boolean critical);

    protected abstract IdeFrame getIdeFrame(Project project);

    private boolean isAppActive() {
      Application app = ApplicationManager.getApplication();

      if (app != null && myAppListener == null) {
        myAppListener = new ApplicationActivationListener() {
          @Override
          public void applicationActivated(IdeFrame ideFrame) {
            hideProgress(ideFrame.getProject(), myCurrentProcessId);
            _setOkBadge(ideFrame, false);
            _setTextBadge(ideFrame, null);
          }

          @Override
          public void applicationDeactivated(IdeFrame ideFrame) {
          }
        };
        app.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, myAppListener);
      }

      return app != null ? app.isActive() : false;
    }
  }


  private static class MacAppIcon extends BaseIcon {

    private static BufferedImage myAppImage;

    private BufferedImage getAppImage() {
      assertIsDispatchThread();

      try {
        if (myAppImage != null) return myAppImage;

        Object app = getApp();
        Image appImage = (Image)getAppMethod("getDockIconImage").invoke(app);

        if (appImage == null) return null;

        int width = appImage.getWidth(null);
        int height = appImage.getHeight(null);
        BufferedImage img = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.drawImage(appImage, null, null);
        myAppImage = img;
      }
      catch (NoSuchMethodException e) {
        return null;
      }
      catch (Exception e) {
        LOG.error(e);
      }

      return myAppImage;
    }

    @Override
    public void _setTextBadge(IdeFrame frame, String text) {
      assertIsDispatchThread();

      try {
        getAppMethod("setDockIconBadge", String.class).invoke(getApp(), text);
      }
      catch (NoSuchMethodException e) {
        return;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public void requestFocus(IdeFrame frame) {
      assertIsDispatchThread();

      try {
        getAppMethod("requestForeground", boolean.class).invoke(getApp(), true);
      }
      catch (NoSuchMethodException e) {
        return;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public void _requestAttention(IdeFrame frame, boolean critical) {
      assertIsDispatchThread();

      try {
        getAppMethod("requestUserAttention", boolean.class).invoke(getApp(), critical);
      }
      catch (NoSuchMethodException e) {
        return;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    protected IdeFrame getIdeFrame(Project project) {
      return null;
    }

    @Override
    public boolean _hideProgress(IdeFrame frame, Object processId) {
      assertIsDispatchThread();

      if (getAppImage() == null) return false;
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) return false;

      setDockIcon(getAppImage());
      myCurrentProcessId = null;
      myLastValue = 0;

      return true;
    }

    @Override
    public void _setOkBadge(IdeFrame frame, boolean visible) {
      assertIsDispatchThread();

      if (getAppImage() == null) return;

      AppImage img = createAppImage();

      if (visible) {
        Icon okIcon = AllIcons.Mac.AppIconOk512;

        int x = img.myImg.getWidth() - okIcon.getIconWidth();
        int y = 0;

        okIcon.paintIcon(JOptionPane.getRootFrame(), img.myG2d, x, y);
      }

      setDockIcon(img.myImg);
    }

    @Override
    public boolean _setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      assertIsDispatchThread();

      if (getAppImage() == null) return false;

      myCurrentProcessId = processId;

      if (Math.abs(myLastValue - value) < 0.02d) return true;

      try {
        int progressHeight = (int)(myAppImage.getHeight() * 0.13);
        int xInset = (int)(myAppImage.getWidth() * 0.05);
        int yInset = (int)(myAppImage.getHeight() * 0.15);

        final int width = myAppImage.getWidth() - xInset * 2;
        final int y = myAppImage.getHeight() - progressHeight - yInset;
        Shape rect = new RoundRectangle2D.Double(xInset, y, width, progressHeight, progressHeight, progressHeight);
        Shape border =
          new RoundRectangle2D.Double(xInset - 1, y - 1, width + 2, progressHeight + 2, (progressHeight + 2), (progressHeight + 2));
        Shape progress = new RoundRectangle2D.Double(xInset + 1, y + 1, (width - 2) * value, progressHeight - 1, (progressHeight - 2),
                                                     (progressHeight - 1));
        AppImage appImg = createAppImage();

        final Color brighter = Color.GRAY.brighter().brighter();
        final Color backGround = new Color(brighter.getRed(), brighter.getGreen(), brighter.getBlue(), 85);
        appImg.myG2d.setColor(backGround);
        appImg.myG2d.fill(rect);
        final Color color = isOk ? scheme.getOkColor() : scheme.getErrorColor();
        final Paint paint = UIUtil.getGradientPaint(xInset + 1, y + 1, color.brighter(),
                                                      xInset + 1, y + progressHeight - 1, color.darker().darker());
        appImg.myG2d.setPaint(paint);
        appImg.myG2d.fill(progress);
        appImg.myG2d.setColor(Color.GRAY.darker().darker());
        appImg.myG2d.draw(rect);
        appImg.myG2d.draw(border);

        setDockIcon(appImg.myImg);

        myLastValue = value;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        myCurrentProcessId = null;
      }

      return true;
    }

    private AppImage createAppImage() {
      BufferedImage current = UIUtil.createImage(getAppImage().getWidth(), getAppImage().getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = current.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(getAppImage(), null, null);
      return new AppImage(current, g);
    }

    private class AppImage {
      BufferedImage myImg;
      Graphics2D myG2d;

      AppImage(BufferedImage img, Graphics2D g2d) {
        myImg = img;
        myG2d = g2d;
      }
    }

    private void setDockIcon(BufferedImage image) {
      try {
        getAppMethod("setDockIconImage", Image.class).invoke(getApp(), image);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    private Method getAppMethod(final String name, Class... args) throws NoSuchMethodException, ClassNotFoundException {
      return getAppClass().getMethod(name, args);
    }

    private Object getApp() throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
      return getAppClass().getMethod("getApplication").invoke(null);
    }

    private Class<?> getAppClass() throws ClassNotFoundException {
      return Class.forName("com.apple.eawt.Application");
    }
  }

  private static class Win7AppIcon extends BaseIcon {
    @Override
    public boolean _setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      myCurrentProcessId = processId;

      if (Math.abs(myLastValue - value) < 0.02d) {
        return true;
      }

      try {
        if (isValid(frame)) {
          Win7TaskBar.setProgress(frame, value, isOk);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }

      myLastValue = value;
      myCurrentProcessId = null;
      return true;
    }

    @Override
    public boolean _hideProgress(IdeFrame frame, Object processId) {
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) {
        return false;
      }

      try {
        if (isValid(frame)) {
          Win7TaskBar.hideProgress(frame);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }

      myCurrentProcessId = null;
      myLastValue = 0;
      return true;
    }

    private static final Color ERROR_COLOR = new Color(197, 54, 13);

    @Override
    public void _setTextBadge(IdeFrame frame, String text) {
      if (!isValid(frame)) {
        return;
      }

      Object icon = null;

      if (text != null) {
        try {
          int size = 55;
          BufferedImage image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();

          int roundSize = 40;
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.setPaint(ERROR_COLOR);
          g.fillRoundRect(size / 2 - roundSize / 2, size / 2 - roundSize / 2, roundSize, roundSize, size, size);

          g.setColor(Color.white);
          Font font = g.getFont();
          g.setFont(new Font(font.getName(), font.getStyle(), 22));
          FontMetrics fontMetrics = g.getFontMetrics();
          int width = fontMetrics.stringWidth(text);
          g.drawString(text, size / 2 - width / 2, size / 2 - fontMetrics.getHeight() / 2 + fontMetrics.getAscent());

          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          Sanselan.writeImage(image, bytes, ImageFormat.IMAGE_FORMAT_ICO, new HashMap());
          icon = Win7TaskBar.createIcon(bytes.toByteArray());
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(frame, icon, icon != null);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private static Object myOkIcon;

    @Override
    public void _setOkBadge(IdeFrame frame, boolean visible) {
      if (!isValid(frame)) {
        return;
      }

      Object icon = null;

      if (visible) {
        synchronized (Win7AppIcon.class) {
          if (myOkIcon == null) {
            try {
              BufferedImage image = ImageIO.read(getClass().getResource("/mac/appIconOk512.png"));
              ByteArrayOutputStream bytes = new ByteArrayOutputStream();
              Sanselan.writeImage(image, bytes, ImageFormat.IMAGE_FORMAT_ICO, new HashMap());
              myOkIcon = Win7TaskBar.createIcon(bytes.toByteArray());
            }
            catch (Throwable e) {
              LOG.error(e);
              myOkIcon = null;
            }
          }

          icon = myOkIcon;
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(frame, icon, false);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public void _requestAttention(IdeFrame frame, boolean critical) {
      try {
        if (isValid(frame)) {
          Win7TaskBar.attention(frame, critical);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    protected IdeFrame getIdeFrame(Project project) {
      return WindowManager.getInstance().getIdeFrame(project);
    }

    @Override
    public void requestFocus(IdeFrame frame) {
    }

    private static boolean isValid(IdeFrame frame) {
      return frame != null && ((Component)frame).isDisplayable();
    }
  }

  private static class EmptyIcon extends AppIcon {
    @Override
    public boolean setProgress(Project project, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean hideProgress(Project project, Object processId) {
      return false;
    }

    @Override
    public void setErrorBadge(Project project, String text) {
    }

    @Override
    public void setOkBadge(Project project, boolean visible) {
    }

    @Override
    public void requestAttention(Project project, boolean critical) {
    }

    @Override
    public void requestFocus(IdeFrame frame) {
    }
  }

  private static void assertIsDispatchThread() {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      if (!app.isUnitTestMode()) {
        app.assertIsDispatchThread();
      }
    }
    else {
      assert EventQueue.isDispatchThread();
    }
  }
}
