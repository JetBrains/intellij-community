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
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class AppUIUtil {
  private AppUIUtil() {
  }

  public static void updateFrameIcon(final Frame frame) {
    try {
      // new API added in JDK 1.6
      final Method method = frame.getClass().getMethod("setIconImages", List.class);
      method.invoke(frame, getAppIconImages());
    }
    catch (Exception e) {
      // fallback to JDK 1.5 API which doesn't support transparent PNGs as frame icons
      final Image image = ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getOpaqueIconUrl());
      frame.setIconImage(image);
    }
  }

  public static List<Image> getAppIconImages() {
    List<Image> images = new ArrayList<Image>();
    images.add(ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getIconUrl()));
    images.add(ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getSmallIconUrl()));
    return images;
  }

  public static void updateDialogIcon(final JDialog dialog) {
    UIUtil.updateDialogIcon(dialog, getAppIconImages());
  }
}
