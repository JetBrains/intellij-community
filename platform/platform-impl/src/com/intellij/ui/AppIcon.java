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
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class AppIcon {

  static Logger LOG = Logger.getInstance("AppIcon");

  static AppIcon ourMacImpl;
  static AppIcon ourWin7Impl;
  static AppIcon ourEmptyImpl;

  public abstract boolean setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

  public abstract boolean hideProgress(Object processId);

  public abstract void setErrorBadge(String text);

  public abstract void setOkBadge(boolean visible);

  public abstract void requestAttention(boolean critical);

  public static AppIcon getInstance() {
    if (ourMacImpl == null) {
      ourMacImpl = new MacAppIcon();
      ourWin7Impl = new Win7AppIcon();
      ourEmptyImpl = new EmptyIcon();
    }

    if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.6")) {
      return ourMacImpl;
    } else if (SystemInfo.isWindows7) {
      return ourWin7Impl;
    } else {
      return ourEmptyImpl;
    }
  }

  private static abstract class BaseIcon extends AppIcon {

    private ApplicationListener myAppListener;
    protected Object myCurrentProcessId;

    @Override
    public final boolean setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      if (!isAppActive() && Registry.is("ide.appIcon.progress") && (myCurrentProcessId == null || myCurrentProcessId.equals(processId))) {
        return _setProgress(processId, scheme, value, isOk);
      } else {
        return false;
      }
    }

    @Override
    public final boolean hideProgress(Object processId) {
      if (Registry.is("ide.appIcon.progress")) {
        return _hideProgress(processId);
      } else {
        return false;
      }
    }

    @Override
    public final void setErrorBadge(String text) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setOkBadge(false);
        _setTextBadge(text);
      }
    }

    @Override
    public final void setOkBadge(boolean visible) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setTextBadge(null);
        _setOkBadge(visible);
      }
    }

    @Override
    public final void requestAttention(boolean critical) {
      if (!isAppActive() && Registry.is("ide.appIcon.requestAttention")) {
        _requestAttention(critical);
      }
    }

    public abstract boolean _setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

    public abstract boolean _hideProgress(Object processId);

    public abstract void _setTextBadge(String text);

    public abstract void _setOkBadge(boolean visible);

    public abstract void _requestAttention(boolean critical);

    private boolean isAppActive() {
      Application app = ApplicationManager.getApplication();

      if (app != null && myAppListener == null) {
        myAppListener = new ApplicationAdapter() {
          @Override
          public void applicationActivated(IdeFrame ideFrame) {
            hideProgress(myCurrentProcessId);
            setOkBadge(false);
            setErrorBadge(null);
          }
        };
        app.addApplicationListener(myAppListener);
      }

      return app != null ? app.isActive() : false;
    }
  }


  private static class MacAppIcon extends BaseIcon {

    private static BufferedImage myAppImage;

    private double myLastValue;

    private BufferedImage getAppImage() {
      assertIsDispatchThread();

      try {
        if (myAppImage != null) return myAppImage;

        Object app = getApp();
        Image appImage = (Image)getAppMethod("getDockIconImage").invoke(app);

        if (appImage == null) return null;

        int width = appImage.getWidth(null);
        int height = appImage.getHeight(null);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
    public void _setTextBadge(String text) {
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
    public void _requestAttention(boolean critical) {
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
    public boolean _hideProgress(Object processId) {
      assertIsDispatchThread();

      if (getAppImage() == null) return false;
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) return false;

      setDockIcon(getAppImage());
      myCurrentProcessId = null;
      myLastValue = 0;

      return true;
    }

    @Override
    public void _setOkBadge(boolean visible) {
      assertIsDispatchThread();

      if (getAppImage() == null) return;

      AppImage img = createAppImage();

      if (visible) {
        Icon okIcon = IconLoader.getIcon("/mac/appIconOk512.png");

        int x = img.myImg.getWidth() - okIcon.getIconWidth();
        int y = 0;

        okIcon.paintIcon(JOptionPane.getRootFrame(), img.myG2d, x, y);
      }

      setDockIcon(img.myImg);
    }

    public boolean _setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      assertIsDispatchThread();

      if (getAppImage() == null) return false;

      myCurrentProcessId = processId;

      if (Math.abs(myLastValue - value) < 0.02d) return true;

      try {
        int progressHeight = (int)(myAppImage.getHeight() * 0.15);
        int xInset = (int)(myAppImage.getWidth() * 0.05);
        int yInset = (int)(myAppImage.getHeight() * 0.15);
        int bound = (int)(myAppImage.getWidth() * 0.03);

        Rectangle progressRec = new Rectangle(new Point(xInset, myAppImage.getHeight() - progressHeight - yInset),
                                              new Dimension(myAppImage.getWidth() - xInset * 2, progressHeight));

        AppImage appImg = createAppImage();

        Rectangle bgRec = new Rectangle(progressRec.x - bound, progressRec.y - bound, progressRec.width + bound * 2, progressRec.height + bound * 2);
        appImg.myG2d.setColor(Color.white);
        appImg.myG2d.fillRect(bgRec.x, bgRec.y, bgRec.width, bgRec.height);
        
        appImg.myG2d.setColor(isOk ? scheme.getOkColor() : scheme.getErrorColor());

        int currentWidth = (int)Math.ceil(progressRec.width * value);
        appImg.myG2d.fillRect(progressRec.x, progressRec.y, currentWidth, progressRec.height);

        appImg.myG2d.setColor(Color.black);
        appImg.myG2d.drawRect(bgRec.x, bgRec.y, bgRec.width - 1, bgRec.height - 1);


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
      BufferedImage current = new BufferedImage(getAppImage().getWidth(), getAppImage().getHeight(), BufferedImage.TYPE_INT_ARGB);
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
    public boolean _setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean _hideProgress(Object processId) {
      return false;
    }

    @Override
    public void _setTextBadge(String text) {
    }

    @Override
    public void _setOkBadge(boolean visible) {
    }

    @Override
    public void _requestAttention(boolean critical) {
    }
  }

  private static class EmptyIcon extends AppIcon {
    @Override
    public boolean setProgress(Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean hideProgress(Object processId) {
      return false;
    }

    @Override
    public void setErrorBadge(String text) {
    }

    @Override
    public void setOkBadge(boolean visible) {
    }

    @Override
    public void requestAttention(boolean critical) {
    }
  }

  private static void assertIsDispatchThread() {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      if (!app.isUnitTestMode()) {
        app.assertIsDispatchThread();
      }
    } else {
      assert EventQueue.isDispatchThread();
    }
  }

}
