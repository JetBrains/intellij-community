// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.*;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.VolatileImage;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * @author gregsh
 */
public final class IdeBackgroundUtil {
  public static final String EDITOR_PROP = "idea.background.editor";
  public static final String FRAME_PROP = "idea.background.frame";

  public static final Key<Boolean> NO_BACKGROUND = Key.create("SUPPRESS_BACKGROUND");

  public enum Fill {
    PLAIN, SCALE, TILE
  }

  public enum Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
  }

  static {
    JBSwingUtilities.addGlobalCGTransform(new MyTransform());
  }

  public static @NotNull Graphics2D withEditorBackground(@NotNull Graphics g, @NotNull JComponent component) {
    return withNamedPainters(g, EDITOR_PROP, component);
  }

  public static @NotNull Graphics2D withFrameBackground(@NotNull Graphics g, @NotNull JComponent component) {
    return withNamedPainters(g, FRAME_PROP, component);
  }

  public static @NotNull Graphics2D getOriginalGraphics(@NotNull Graphics g) {
    return g instanceof MyGraphics? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
  }

  private static @NotNull Graphics2D withNamedPainters(@NotNull Graphics g,
                                                       @NotNull String paintersName,
                                                       @NotNull JComponent component) {
    Boolean noBackground = ClientProperty.get(component, NO_BACKGROUND);
    if (Boolean.TRUE.equals(noBackground)) {
      return MyGraphics.unwrap(g);
    }

    boolean checkLayer = !Boolean.FALSE.equals(noBackground);
    JRootPane rootPane = null;
    for (Component c = component, p = null; c != null && rootPane == null; p = c, c = c.getParent()) {
      if (c instanceof JRootPane) {
        rootPane = (JRootPane)c;
      }
      if (checkLayer && c instanceof JLayeredPane && p != null && ((JLayeredPane)c).getLayer(p) == JLayeredPane.POPUP_LAYER) {
        break;
      }
    }
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    PainterHelper helper = glassPane instanceof IdeGlassPaneImpl ? ((IdeGlassPaneImpl)glassPane).getNamedPainters$intellij_platform_ide_impl(paintersName) : null;
    if (helper == null || !helper.needsRepaint()) {
      return MyGraphics.unwrap(g);
    }
    return MyGraphics.wrap(g, helper, component);
  }

  static void initEditorPainters(@NotNull IdeGlassPaneImpl glassPane) {
    PainterHelper.initWallpaperPainter(EDITOR_PROP, glassPane.getNamedPainters$intellij_platform_ide_impl(EDITOR_PROP));
  }

  static void initFramePainters(@NotNull IdeGlassPaneImpl glassPane) {
    PainterHelper painters = glassPane.getNamedPainters$intellij_platform_ide_impl(FRAME_PROP);
    PainterHelper.initWallpaperPainter(FRAME_PROP, painters);

    painters.addPainter(new AbstractPainter() {
      EditorEmptyTextPainter p = null;

      @Override
      public boolean needsRepaint() {
        return true;
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        if (p == null) {
          p = ApplicationManager.getApplication().getService(EditorEmptyTextPainter.class);
        }
        p.paintEmptyText((JComponent)component, g);
      }
    }, null);
  }

  public static void resetBackgroundImagePainters() {
    PainterHelper.resetWallpaperPainterCache();
    repaintAllWindows();
  }

  public static @NotNull Color getIdeBackgroundColor() {
    return JBColor.lazy(() -> {
      return StartupUiUtil.isUnderDarcula() ? Gray._40 : ColorUtil.darker(JBColor.PanelBackground, 3);
    });
  }

  public static void createTemporaryBackgroundTransform(@NotNull JPanel root, String tmp, @NotNull Disposable disposable) {
    PainterHelper paintersHelper = new PainterHelper(root);
    PainterHelper.initWallpaperPainter(tmp, paintersHelper);
    createTemporaryBackgroundTransform(root, paintersHelper, disposable);
  }

  public static void createTemporaryBackgroundTransform(JComponent root,
                                                        Image image,
                                                        Fill fill,
                                                        Anchor anchor,
                                                        float alpha,
                                                        Insets insets,
                                                        Disposable disposable) {
    PainterHelper paintersHelper = new PainterHelper(root);
    paintersHelper.addPainter(PainterHelper.newImagePainter(image, fill, anchor, alpha, insets), root);
    createTemporaryBackgroundTransform(root, paintersHelper, disposable);
  }

  private static void createTemporaryBackgroundTransform(JComponent root, PainterHelper painterHelper, Disposable disposable) {
    Disposer.register(disposable, JBSwingUtilities.addGlobalCGTransform((c, g) -> {
      if (!UIUtil.isAncestor(root, c)) {
        return g;
      }

      Boolean noBackground = ClientProperty.get(c, NO_BACKGROUND);
      if (Boolean.TRUE.equals(noBackground)) {
        return MyGraphics.unwrap(g);
      }

      return MyGraphics.wrap(g, painterHelper, c);
    }));
  }

  public static @NotNull String getBackgroundSpec(@Nullable Project project, @NotNull String propertyName) {
    String spec = project == null || project.isDisposed() ? null : PropertiesComponent.getInstance(project).getValue(propertyName);
    if (spec == null) {
      spec = PropertiesComponent.getInstance().getValue(propertyName);
    }
    return spec == null ? System.getProperty(propertyName, "") : spec;
  }

  public static boolean isEditorBackgroundImageSet(@Nullable Project project) {
    return Strings.isNotEmpty(getBackgroundSpec(project, EDITOR_PROP));
  }

  public static void repaintAllWindows() {
    UISettings.getInstance().fireUISettingsChanged();
    for (Window window : Window.getWindows()) {
      window.repaint();
    }
  }

  static final RenderingHints.Key ADJUST_ALPHA = new RenderingHints.Key(1) {
    @Override
    public boolean isCompatibleValue(Object val) {
      return val instanceof Boolean;
    }
  };

  @ApiStatus.Internal
  public static final RenderingHints.Key NO_BACKGROUND_HINT = new RenderingHints.Key(2) {
    @Override
    public boolean isCompatibleValue(Object val) {
      return val instanceof Boolean;
    }
  };

  private static final class MyGraphics extends Graphics2DDelegate {
    final PainterHelper helper;
    final PainterHelper.Offsets offsets;
    Predicate<? super Color> preserved;

    static Graphics2D wrap(Graphics g, PainterHelper helper, JComponent component) {
      MyGraphics gg = g instanceof MyGraphics ? (MyGraphics)g : null;
      return new MyGraphics(gg != null ? gg.myDelegate : g, helper, helper.computeOffsets(g, component), gg != null ? gg.preserved : null);
    }

    static Graphics2D unwrap(Graphics g) {
      return g instanceof MyGraphics ? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
    }

    MyGraphics(Graphics g, PainterHelper helper, PainterHelper.Offsets offsets, Predicate<? super Color> preserved) {
      super((Graphics2D)g);
      this.helper = helper;
      this.offsets = offsets;
      this.preserved = preserved;
    }

    @Override
    public @NotNull Graphics create() {
      return new MyGraphics(getDelegate().create(), helper, offsets, preserved);
    }

    private Boolean isNoBackground() {
      Object obj = getRenderingHint(NO_BACKGROUND_HINT);
      return obj != null && Boolean.TRUE.equals(obj);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
      super.clearRect(x, y, width, height);
      runAllPainters(x, y, width, height, null, getColor());
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
      super.fillRect(x, y, width, height);
      runAllPainters(x, y, width, height, null, getColor());
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
      super.fillArc(x, y, width, height, startAngle, arcAngle);
      runAllPainters(x, y, width, height, new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.PIE), getColor());
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
      super.fillOval(x, y, width, height);
      runAllPainters(x, y, width, height, new Ellipse2D.Double(x, y, width, height), getColor());
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
      super.fillPolygon(xPoints, yPoints, nPoints);
      Polygon s = new Polygon(xPoints, yPoints, nPoints);
      Rectangle r = s.getBounds();
      runAllPainters(r.x, r.y, r.width, r.height, s, getColor());
    }

    @Override
    public void fillPolygon(Polygon s) {
      super.fillPolygon(s);
      Rectangle r = s.getBounds();
      runAllPainters(r.x, r.y, r.width, r.height, s, getColor());
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
      super.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
      runAllPainters(x, y, width, height, new RoundRectangle2D.Double(x, y, width, height, arcHeight, arcHeight), getColor());
    }

    @Override
    public void fill(Shape s) {
      super.fill(s);
      Rectangle r = s.getBounds();
      runAllPainters(r.x, r.y, r.width, r.height, s, getColor());
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
      super.drawImage(img, op, x, y);
      runAllPainters(x, y, img.getWidth(), img.getHeight(), null, img);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, width, height, observer);
      runAllPainters(x, y, width, height, null, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color c,ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, width, height, c, observer);
      runAllPainters(x, y, width, height, null, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, observer);
      runAllPainters(x, y, img.getWidth(null), img.getHeight(null), null, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color c, ImageObserver observer) {
      boolean b = super.drawImage(img, x, y, c, observer);
      runAllPainters(x, y, img.getWidth(null), img.getHeight(null), null, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
      boolean b = super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
      runAllPainters(dx1, dy1, dx2 - dx1, dy2 - dy1, null, img);
      return b;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color c, ImageObserver observer) {
      boolean b = super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, c, observer);
      runAllPainters(dx1, dy1, dx2 - dx1, dy2 - dy1, null, img);
      return b;
    }

    void runAllPainters(int x, int y, int width, int height, @Nullable Shape sourceShape, @Nullable Object reason) {
      if (width <= 1 || height <= 1 || isNoBackground()) return;
      boolean hasAlpha;
      if (reason instanceof Color) {
        hasAlpha = ((Color)reason).getAlpha() < 255;
      }
      else if (reason instanceof BufferedImage) {
        hasAlpha = ((BufferedImage)reason).getColorModel().hasAlpha();
      }
      else {
        hasAlpha = !(reason instanceof VolatileImage) || ((VolatileImage)reason).getTransparency() != Transparency.OPAQUE;
      }

      // skip painters when alpha is already present
      if (hasAlpha) {
        return;
      }

      boolean preserve = preserved != null && reason instanceof Color && preserved.test((Color)reason);
      if (preserve) {
        myDelegate.setRenderingHint(ADJUST_ALPHA, Boolean.TRUE);
      }
      Graphics2D clipped = (Graphics2D)create();
      try {
        clipped.clip(sourceShape != null ? sourceShape : new Rectangle(x, y, width, height));
        helper.runAllPainters(clipped, offsets);
      }
      finally {
        clipped.dispose();
      }
      if (preserve) {
        myDelegate.setRenderingHint(ADJUST_ALPHA, Boolean.FALSE);
      }
    }
  }

  private static final class MyTransform implements BiFunction<JComponent, Graphics2D, Graphics2D> {
    @Override
    public Graphics2D apply(@NotNull JComponent c, @NotNull Graphics2D g) {
      Graphics2D original = MyGraphics.unwrap(g);
      if (c instanceof EditorsSplitters) {
        return withFrameBackground(original, c);
      }

      Editor editor = obtainEditor(c);
      if (editor instanceof EditorImpl editorImpl) {
        if (c instanceof EditorComponentImpl && (editorImpl.isDumb() || editorImpl.isStickyLinePainting())) {
          return original;
        }
        if (c instanceof EditorGutterComponentEx && editorImpl.isStickyLinePainting()) {
          return original;
        }
      }

      Graphics2D gg = withEditorBackground(original, c);
      if (gg instanceof MyGraphics) {
        ((MyGraphics)gg).preserved = editor != null ? getEditorPreserveColorCondition((EditorEx)editor) : getGeneralPreserveColorCondition(c);
      }
      return gg;
    }

    private static @Nullable Editor obtainEditor(@Nullable JComponent c) {
      Component view = c instanceof JViewport ? ((JViewport)c).getView() : c;
      return view instanceof EditorComponentImpl o ? o.getEditor() :
             view instanceof EditorGutterComponentEx o ? o.getEditor() :
             null;
    }

    private static @NotNull Predicate<Color> getEditorPreserveColorCondition(@NotNull EditorEx editor) {
      Color background1 = editor.getBackgroundColor();
      Color background2 = editor.getGutterComponentEx().getBackground();
      return color -> color != background1 && color != background2;
    }

    private static @NotNull Predicate<Color> getGeneralPreserveColorCondition(JComponent c) {
      Component view = c instanceof JViewport ? ((JViewport)c).getView() : c;
      Color selection1 = view instanceof JTree ? UIUtil.getTreeSelectionBackground(true) :
                         view instanceof JList ? UIUtil.getListSelectionBackground(true) :
                         view instanceof JTable ? UIUtil.getTableSelectionBackground(true) :
                         view instanceof JTextComponent ? ((JTextComponent)view).getSelectionColor() :
                         view instanceof JMenuBar || view instanceof JMenu ? UIManager.getColor("Menu.selectionBackground") :
                         null;
      Color selection2 = view instanceof JTree ? UIUtil.getTreeSelectionBackground(false) :
                         view instanceof JList ? UIUtil.getListSelectionBackground(false) :
                         view instanceof JTable ? UIUtil.getTableSelectionBackground(false) :
                         null;
      return color -> color == selection1 || color == selection2;
    }
  }
}
