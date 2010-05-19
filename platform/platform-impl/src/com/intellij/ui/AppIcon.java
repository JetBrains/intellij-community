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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

public abstract class AppIcon {

  static Logger LOG = Logger.getInstance("AppIcon");

  static AppIcon ourMacImpl;
  static AppIcon ourEmptyImpl;

  public abstract void setProgress(double value, boolean isOk);

  public abstract void hideProgress();

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

    private static BufferedImage myCurrentImage;

    private boolean myProgressShown;

    private MacAppIcon() {
      try {
        Object app = getApp().invoke(null);
        Image appImage = (Image)getAppMethod("getDockIconImage").invoke(app);

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
    }

    private Method getAppMethod(final String name, Class... args) throws NoSuchMethodException, ClassNotFoundException {
      return getAppClass().getMethod(name, args);
    }

    private Method getApp() throws NoSuchMethodException, ClassNotFoundException {
      return getAppClass().getMethod("getApplication");
    }

    private Class<?> getAppClass() throws ClassNotFoundException {
      return Class.forName("com.apple.eawt.Application");
    }

    @Override
    public void hideProgress() {
      try {
        if (myProgressShown) {
          setDockIcon(myAppImage);
        }
      }
      finally {
        myProgressShown = false;
      }
    }

    public void setProgress(double progress, boolean isOk) {
      try {
        int progressHeight = 45;
        int xInset = 30;
        int yInset = 10;
        int arc = 25;

        Rectangle progressRec = new Rectangle(new Point(xInset, myAppImage.getHeight() - yInset - progressHeight),
                                              new Dimension(myAppImage.getWidth() - xInset * 2, progressHeight));

        myCurrentImage = new BufferedImage(myAppImage.getWidth(), myAppImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = myCurrentImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(myAppImage, null, null);

        g.setColor(Color.black);
        g.fillRoundRect(progressRec.x, progressRec.y, progressRec.width, progressRec.height, arc, arc);

        g.setColor(isOk ? Color.green : Color.red);

        int currentWidth = (int)Math.ceil(progressRec.width * progress);

        g.fillRoundRect(progressRec.x, progressRec.y, currentWidth, progressRec.height, arc, arc);

        setDockIcon(myCurrentImage);
      }
      catch (Exception e) {
        LOG.error(e);
      } finally {
        myProgressShown = true;
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
  }

  private static class EmptyIcon extends AppIcon {
    @Override
    public void setProgress(double value, boolean isOk) {
    }

    @Override
    public void hideProgress() {
    }
  }


}
