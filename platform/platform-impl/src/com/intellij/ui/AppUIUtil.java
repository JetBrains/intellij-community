/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AppUIUtil {
  private static final String VENDOR_PREFIX = "jetbrains-";

  private static boolean isDebugMode() {
    return "true".equals(System.getProperty("idea.debug.mode"));
  }

  public static void updateFrameIcon(final Frame frame) {
    frame.setIconImages(getAppIconImages());
  }

  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"})
  public static List<Image> getAppIconImages() {
    final List<Image> images = new ArrayList<Image>();
    images.add(com.intellij.util.ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getIconUrl()));
    images.add(com.intellij.util.ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getSmallIconUrl()));
    return images;
  }

  public static void updateDialogIcon(final JDialog dialog) {
    UIUtil.updateDialogIcon(dialog, getAppIconImages());
  }

  public static void invokeLaterIfProjectAlive(@NotNull final Project project, @NotNull final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      runnable.run();
    }
    else {
      application.invokeLater(runnable, new Condition() {
        @Override
        public boolean value(Object o) {
          return (!project.isOpen()) || project.isDisposed();
        }
      });
    }
  }

  public static void updateFrameClass() {
    try {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      final Class<? extends Toolkit> aClass = toolkit.getClass();
      if ("sun.awt.X11.XToolkit".equals(aClass.getName())) {
        final Field awtAppClassName = aClass.getDeclaredField("awtAppClassName");
        awtAppClassName.setAccessible(true);
        awtAppClassName.set(toolkit, getFrameClass());
      }
    }
    catch (Exception ignore) { }
  }

  public static String getFrameClass() {
    final String name = ApplicationNamesInfo.getInstance().getProductName().toLowerCase();
    String wmClass = VENDOR_PREFIX + StringUtil.replaceChar(name, ' ', '-');
    if (isDebugMode()) {
      wmClass += "-debug";
    }
    return PlatformUtils.isCommunity() ? wmClass + "-ce" : wmClass;
  }
}
