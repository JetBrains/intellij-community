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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * @author gregsh
 */
public class IdeBackgroundUtil {

  public static void paintEditorBackground(@NotNull Graphics g, @NotNull JComponent component) {
    JRootPane rootPane = component.getRootPane();
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    if (glassPane instanceof IdeGlassPaneImpl) {
      ((IdeGlassPaneImpl)glassPane).getNamedPainters("editor").paint(g, component);
    }
  }

  public static void paintFrameBackground(@NotNull Graphics g, @NotNull JComponent component) {
    JRootPane rootPane = component.getRootPane();
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    if (glassPane instanceof IdeGlassPaneImpl) {
      ((IdeGlassPaneImpl)glassPane).getNamedPainters("ide").paint(g, component);
    }
  }

  public static void initEditorPainters(@NotNull PaintersHelper painters) {
    painters.addPainter(PaintersHelper.newWallpaperPainter("idea.wallpaper.editor"), null);
  }

  public static void initFramePainters(@NotNull PaintersHelper painters) {
    painters.addPainter(PaintersHelper.newWallpaperPainter("idea.wallpaper.ide"), null);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String path = UIUtil.isUnderDarcula()? appInfo.getEditorBackgroundImageUrl() : null;
    URL url = path == null ? null : appInfo.getClass().getResource(path);
    Image centerImage = url == null ? null : ImageLoader.loadFromUrl(url);

    if (centerImage != null) {
      painters.addPainter(PaintersHelper.newImagePainter(centerImage, PaintersHelper.FillType.TOP_CENTER, 1.0f, JBUI.insets(5, 0, 0, 0)), null);
    }
  }

  @Nullable
  public static Color getIdeBackgroundColor() {
    Color result = UIUtil.getSlightlyDarkerColor(UIUtil.getPanelBackground());
    return UIUtil.isUnderDarcula() ? new Color(40, 40, 41) : UIUtil.getSlightlyDarkerColor(UIUtil.getSlightlyDarkerColor(result));
  }

}
