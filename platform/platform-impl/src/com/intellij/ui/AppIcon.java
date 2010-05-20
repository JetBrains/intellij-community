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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class AppIcon {

  static Logger LOG = Logger.getInstance("AppIcon");

  static AppIcon ourMacImpl;
  static AppIcon ourEmptyImpl;

  public abstract boolean setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

  public abstract boolean hideProgress(IdeFrame frame, Object processId);

  public abstract void setBadge(String text);

  public abstract void requestAttention(boolean critical);

  public static AppIcon getInstance() {
    if (ourMacImpl == null) {
      ourMacImpl = new MacAppIcon();
      ourEmptyImpl = new EmptyIcon();
    }

    if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.6")) {
      return ourMacImpl;
    }
    else {
      return ourEmptyImpl;
    }
  }


  private static class MacAppIcon extends AppIcon {

    private static BufferedImage myAppImage;

    private Object myCurrentProcessId;

    private double myLastValue;

    private BufferedImage getAppImage() {
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
      catch (Exception e) {
        LOG.error(e);
      }

      return myAppImage;
    }

    @Override
    public void setBadge(String text) {
      try {
        getAppMethod("setDockIconBadge", String.class).invoke(getApp(), text);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public void requestAttention(boolean critical) {
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
    public boolean hideProgress(IdeFrame frame, Object processId) {
      if (getAppImage() == null) return false;
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) return false;

      setDockIcon(getAppImage());
      myCurrentProcessId = null;
      myLastValue = 0;

      return true;
    }

    public boolean setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      if (getAppImage() == null) return false;
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) return false;

      myCurrentProcessId = processId;

      if (Math.abs(myLastValue - value) < 0.01d) return true;

      try {
        int progressHeight = 20;
        int xInset = 15;
        int yInset = 10;
        int bound = 2;

        Rectangle progressRec = new Rectangle(new Point(xInset, myAppImage.getHeight() - progressHeight - yInset),
                                              new Dimension(myAppImage.getWidth() - xInset * 2, progressHeight));

        BufferedImage current = new BufferedImage(myAppImage.getWidth(), myAppImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = current.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(getAppImage(), null, null);

        Rectangle bgRec = new Rectangle(progressRec.x - bound, progressRec.y - bound, progressRec.width + bound * 2, progressRec.height + bound * 2);
        g.setColor(Color.white);
        g.fillRect(bgRec.x, bgRec.y, bgRec.width, bgRec.height);


        g.setColor(Color.black);
        g.fillRect(progressRec.x, progressRec.y, progressRec.width, progressRec.height);

        g.setColor(isOk ? scheme.getOkColor() : scheme.getErrorColor());

        int currentWidth = (int)Math.ceil(progressRec.width * value);

        g.fillRect(progressRec.x, progressRec.y, currentWidth, progressRec.height);


        setDockIcon(current);

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

  private static class EmptyIcon extends AppIcon {
    @Override
    public boolean setProgress(IdeFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean hideProgress(IdeFrame frame, Object processId) {
      return false;
    }

    @Override
    public void setBadge(String text) {
    }

    @Override
    public void requestAttention(boolean critical) {
    }
  }


}
