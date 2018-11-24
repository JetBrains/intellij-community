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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Graphics2DDelegate;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.ImageLoader;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.net.URL;
import java.util.Set;

/**
 * @author gregsh
 */
public class IdeBackgroundUtil {

  public static final String EDITOR_PROP = "idea.background.editor";
  public static final String FRAME_PROP = "idea.background.frame";
  public static final String TARGET_PROP = "idea.background.target";

  static {
    JBSwingUtilities.addGlobalCGTransform(new MyTransform());
  }

  @NotNull
  public static Graphics2D withEditorBackground(@NotNull Graphics g, @NotNull JComponent component) {
    if (suppressBackground(component)) return (Graphics2D)g;
    return withNamedPainters(g, EDITOR_PROP, component);
  }

  @NotNull
  public static Graphics2D withFrameBackground(@NotNull Graphics g, @NotNull JComponent component) {
    if (suppressBackground(component)) return (Graphics2D)g;
    return withNamedPainters(g, FRAME_PROP, component);
  }

  private static boolean suppressBackground(JComponent component) {
    String type = getComponentType(component);
    if (type == null) return false;
    String spec = System.getProperty(TARGET_PROP, "*");
    boolean allInclusive = spec.startsWith("*");
    return allInclusive && spec.contains("-" + type) || !allInclusive && !spec.contains(type);
  }

  private static final Set<String> ourKnownNames = ContainerUtil.newHashSet("navbar", "terminal");
  private static String getComponentType(JComponent component) {
    return component instanceof JTree ? "tree" :
           component instanceof JList ? "list" :
           component instanceof JTable ? "table" :
           component instanceof JViewport ? "viewport" :
           component instanceof ActionToolbar ? "toolbar" :
           component instanceof EditorsSplitters ? "frame" :
           component instanceof EditorComponentImpl ? "editor" :
           component instanceof EditorGutterComponentEx ? "editor" :
           component instanceof JBLoadingPanel ? "loading" :
           component instanceof JBTabs ? "tabs" :
           component instanceof ToolWindowHeader ? "title" :
           component instanceof JBPanelWithEmptyText ? "panel" :
           component instanceof JPanel && ourKnownNames.contains(component.getName()) ? component.getName() :
           null;
  }

  @NotNull
  public static Graphics2D getOriginalGraphics(@NotNull Graphics g) {
    return g instanceof MyGraphics? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
  }

  @NotNull
  public static Graphics2D withNamedPainters(@NotNull Graphics g, @NotNull String paintersName, @NotNull final JComponent component) {
    JRootPane rootPane = component.getRootPane();
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    PaintersHelper helper = glassPane instanceof IdeGlassPaneImpl? ((IdeGlassPaneImpl)glassPane).getNamedPainters(paintersName) : null;
    if (helper == null || !helper.needsRepaint()) return (Graphics2D)g;
    return MyGraphics.wrap(g, helper, component);
  }

  public static void initEditorPainters(@NotNull IdeGlassPaneImpl glassPane) {
    PaintersHelper.initWallpaperPainter(EDITOR_PROP, glassPane.getNamedPainters(EDITOR_PROP));
  }

  public static void initFramePainters(@NotNull IdeGlassPaneImpl glassPane) {
    PaintersHelper painters = glassPane.getNamedPainters(FRAME_PROP);
    PaintersHelper.initWallpaperPainter(FRAME_PROP, painters);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String path = /*UIUtil.isUnderDarcula()? appInfo.getEditorBackgroundImageUrl() : */null;
    URL url = path == null ? null : appInfo.getClass().getResource(path);
    Image centerImage = url == null ? null : ImageLoader.loadFromUrl(url);

    if (centerImage != null) {
      painters.addPainter(PaintersHelper.newImagePainter(centerImage, PaintersHelper.Fill.PLAIN, PaintersHelper.Place.TOP_CENTER, 1.0f, JBUI.insets(10, 0, 0, 0)), null);
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

  public static void createTemporaryBackgroundTransform(JPanel root, String tmp, Disposable disposable) {
    PaintersHelper paintersHelper = new PaintersHelper(root);
    PaintersHelper.initWallpaperPainter(tmp, paintersHelper);
    Disposer.register(disposable, JBSwingUtilities.addGlobalCGTransform((t, v) -> {
      if (!UIUtil.isAncestor(root, t)) return v;
      return MyGraphics.wrap(v, paintersHelper, t);
    }));
  }

  @NotNull
  public static String getBackgroundSpec(@Nullable Project project, @NotNull String propertyName) {
    String spec = project == null ? null : PropertiesComponent.getInstance(project).getValue(propertyName);
    if (spec == null) spec = PropertiesComponent.getInstance().getValue(propertyName);
    return StringUtil.notNullize(spec, System.getProperty(propertyName, ""));
  }

  public static void repaintAllWindows() {
    for (Window window : Window.getWindows()) {
      window.repaint();
    }
  }

  private static class MyGraphics extends Graphics2DDelegate {
    final PaintersHelper helper;
    final int[] offsets;
    
    static Graphics2D wrap(Graphics g, PaintersHelper helper, JComponent component) {
      return new MyGraphics(g instanceof MyGraphics ? ((MyGraphics)g).getDelegate() : g,
                            helper, helper.computeOffsets(g, component));
    }

    MyGraphics(Graphics g, PaintersHelper helper, int[] offsets) {
      super((Graphics2D)g);
      this.helper = helper;
      this.offsets = offsets;
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics(getDelegate().create(), helper, offsets);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
      super.clearRect(x, y, width, height);
      runAllPainters(x, y, width, height, getColor());
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
      super.fillRect(x, y, width, height);
      runAllPainters(x, y, width, height, getColor());
    }

    @Override
    public void fill(Shape s) {
      super.fill(s);
      Rectangle r = s.getBounds();
      runAllPainters(r.x, r.y, r.width, r.height, getColor());
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
      super.drawImage(img, op, x, y);
      runAllPainters(x, y, img.getWidth(), img.getHeight(), img);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, width, height, observer);
      runAllPainters(x, y, width, height, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, observer);
      runAllPainters(x, y, img.getWidth(null), img.getHeight(null), img);
      return b;
    }

    void runAllPainters(int x, int y, int width, int height, Object reason) {
      if (width <= 1 || height <= 1) return;
      // skip painters for transparent 'reasons'
      if (reason instanceof Color && ((Color)reason).getAlpha() < 255) return;
      if (reason instanceof Image) {
        if (!(reason instanceof BufferedImage)) return;
        if (((BufferedImage)reason).getColorModel().hasAlpha()) return;
      }

      Shape s = getClip();
      Rectangle newClip = s == null ? new Rectangle(x, y, width, height) :
                          SwingUtilities.computeIntersection(x, y, width, height, s.getBounds());
      setClip(newClip);
      helper.runAllPainters(getDelegate(), offsets);
      setClip(s);
    }
  }

  private static class MyTransform implements PairFunction<JComponent, Graphics2D, Graphics2D> {
    @Override
    public Graphics2D fun(JComponent c, Graphics2D g) {
      String type = getComponentType(c);
      if ("editor".equals(type)) {
        Editor editor = c instanceof EditorComponentImpl ? ((EditorComponentImpl)c).getEditor() :
                        c instanceof EditorGutterComponentEx ? CommonDataKeys.EDITOR.getData((DataProvider)c) : null;
        if (Boolean.TRUE.equals(EditorTextField.SUPPLEMENTARY_KEY.get(editor))) return g;
      }
      if ("frame".equals(type)) return withFrameBackground(g, c);
      return type != null ? withEditorBackground(g, c) : g;
    }
  }
}
