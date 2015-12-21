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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.ui.Graphics2DDelegate;
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

  @NotNull
  public static Graphics2D withEditorBackground(@NotNull Graphics g, @NotNull final JComponent component) {
    return withNamedPainters(g, "editor", component);
  }

  @NotNull
  public static Graphics2D withFrameBackground(@NotNull Graphics g, @NotNull final JComponent component) {
    return withNamedPainters(g, "ide", component);
  }

  @NotNull
  public static Graphics2D getOriginalGraphics(@NotNull Graphics g) {
    return g instanceof MyGraphics? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
  }

  @NotNull
  public static Graphics2D withNamedPainters(@NotNull Graphics g, @NotNull String paintersName, @NotNull final JComponent component) {
    JRootPane rootPane = component.getRootPane();
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    final PaintersHelper helper = glassPane instanceof IdeGlassPaneImpl? ((IdeGlassPaneImpl)glassPane).getNamedPainters(paintersName) : null;
    if (helper == null || !helper.hasPainters()) return (Graphics2D)g;
    return new MyGraphics(g, helper, component);
  }

  public static void initEditorPainters(@NotNull PaintersHelper painters) {
    PaintersHelper.initWallpaperPainter("idea.wallpaper.editor", painters);
  }

  public static void initFramePainters(@NotNull PaintersHelper painters) {
    PaintersHelper.initWallpaperPainter("idea.wallpaper.ide", painters);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String path = /*UIUtil.isUnderDarcula()? appInfo.getEditorBackgroundImageUrl() : */null;
    URL url = path == null ? null : appInfo.getClass().getResource(path);
    Image centerImage = url == null ? null : ImageLoader.loadFromUrl(url);

    if (centerImage != null) {
      painters.addPainter(PaintersHelper.newImagePainter(centerImage, PaintersHelper.FillType.TOP_CENTER, 1.0f, JBUI.insets(10, 0, 0, 0)), null);
    }
    painters.addPainter(new AbstractPainter() {
      EditorEmptyTextPainter p = ServiceManager.getService(EditorEmptyTextPainter.class);

      @Override
      public boolean needsRepaint() {
        return true;
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        p.paintEmptyText((JComponent)component, g);
      }
    }, null);

  }

  @Nullable
  public static Color getIdeBackgroundColor() {
    Color result = UIUtil.getSlightlyDarkerColor(UIUtil.getPanelBackground());
    return UIUtil.isUnderDarcula() ? new Color(40, 40, 41) : UIUtil.getSlightlyDarkerColor(UIUtil.getSlightlyDarkerColor(result));
  }

  private static class MyGraphics extends Graphics2DDelegate {
    private final PaintersHelper myHelper;
    private final JComponent myComponent;

    public MyGraphics(Graphics g, PaintersHelper helper, JComponent component) {
      super((Graphics2D)g);
      myHelper = helper;
      myComponent = component;
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics(super.create(), myHelper, myComponent);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
      super.clearRect(x, y, width, height);
      processPainters(x, y, width, height);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
      super.fillRect(x, y, width, height);
      processPainters(x, y, width, height);
    }

    void processPainters(int x, int y, int width, int height) {
      Shape s = getClip();
      Rectangle newClip = s == null ? new Rectangle(x, y, width, height) :
                          SwingUtilities.computeIntersection(x, y, width, height, s.getBounds());
      setClip(newClip);
      myHelper.paint(getDelegate(), myComponent);
      setClip(s);
    }
  }
}
