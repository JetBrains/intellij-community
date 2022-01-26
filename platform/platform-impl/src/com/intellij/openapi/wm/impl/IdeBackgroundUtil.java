// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.toolWindow.ToolWindowHeader;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
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
  public static final String TARGET_PROP = "idea.background.target";

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
    if (suppressBackground(component)) {
      return (Graphics2D)g;
    }
    return withNamedPainters(g, EDITOR_PROP, component);
  }

  public static @NotNull Graphics2D withFrameBackground(@NotNull Graphics g, @NotNull JComponent component) {
    if (suppressBackground(component)) {
      return (Graphics2D)g;
    }
    return withNamedPainters(g, FRAME_PROP, component);
  }

  private static boolean suppressBackground(@NotNull JComponent component) {
    String type = getComponentType(component);
    if (type == null) {
      return false;
    }
    String spec = System.getProperty(TARGET_PROP, "*");
    boolean allInclusive = spec.startsWith("*");
    return allInclusive ? spec.contains("-" + type) : !spec.contains(type);
  }

  private static boolean isKnownName(@Nullable String name) {
    //noinspection SpellCheckingInspection
    return name != null && (name.equals("navbar") || name.equals("terminal"));
  }

  @NonNls
  private static @Nullable String getComponentType(JComponent component) {
    return component instanceof JTree ? "tree" :
           component instanceof JList ? "list" :
           component instanceof JTable ? "table" :
           component instanceof JViewport ? "viewport" :
           component instanceof JTabbedPane ? "tabs" :
           component instanceof JButton ? "button" :
           component instanceof ActionToolbar ? "toolbar" :
           component instanceof StatusBar ? "statusbar" :
           component instanceof JMenuBar || component instanceof JMenu? "menubar" :
           component instanceof Stripe ? "stripe" :
           component instanceof EditorsSplitters ? "frame" :
           component instanceof EditorComponentImpl ? "editor" :
           component instanceof EditorGutterComponentEx ? "editor" :
           component instanceof JBLoadingPanel ? "loading" :
           component instanceof JBTabs ? "tabs" :
           component instanceof ToolWindowHeader ? "title" :
           component instanceof JBPanelWithEmptyText ? "panel" :
           component instanceof JPanel && isKnownName(component.getName()) ? component.getName() :
           null;
  }

  public static @NotNull Graphics2D getOriginalGraphics(@NotNull Graphics g) {
    return g instanceof MyGraphics? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
  }

  private static @NotNull Graphics2D withNamedPainters(@NotNull Graphics g, @NotNull String paintersName, final @NotNull JComponent component) {
    JRootPane rootPane = component.getRootPane();
    Component glassPane = rootPane == null ? null : rootPane.getGlassPane();
    PaintersHelper helper = glassPane instanceof IdeGlassPaneImpl? ((IdeGlassPaneImpl)glassPane).getNamedPainters(paintersName) : null;
    if (helper == null || !helper.needsRepaint()) return (Graphics2D)g;
    return MyGraphics.wrap(g, helper, component);
  }

  static void initEditorPainters(@NotNull IdeGlassPaneImpl glassPane) {
    PaintersHelper.initWallpaperPainter(EDITOR_PROP, glassPane.getNamedPainters(EDITOR_PROP));
  }

  static void initFramePainters(@NotNull IdeGlassPaneImpl glassPane) {
    PaintersHelper painters = glassPane.getNamedPainters(FRAME_PROP);
    PaintersHelper.initWallpaperPainter(FRAME_PROP, painters);

    //Image centerImage = null;
    //if (centerImage != null) {
    //  painters.addPainter(PaintersHelper.newImagePainter(centerImage, Fill.PLAIN, Anchor.TOP_CENTER, 1.0f, JBUI.insets(10, 0, 0, 0)), null);
    //}

    painters.addPainter(new AbstractPainter() {
      final EditorEmptyTextPainter p = ApplicationManager.getApplication().getService(EditorEmptyTextPainter.class);

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

  public static @NotNull Color getIdeBackgroundColor() {
    return JBColor.lazy(() -> {
      Color light = ColorUtil.darker(UIUtil.getPanelBackground(), 3);
      return StartupUiUtil.isUnderDarcula() ? Gray._40 : light;
    });
  }

  public static void createTemporaryBackgroundTransform(@NotNull JPanel root, String tmp, @NotNull Disposable disposable) {
    PaintersHelper paintersHelper = new PaintersHelper(root);
    PaintersHelper.initWallpaperPainter(tmp, paintersHelper);
    Disposer.register(disposable, JBSwingUtilities.addGlobalCGTransform((t, v) -> {
      if (!UIUtil.isAncestor(root, t)) return v;
      return MyGraphics.wrap(v, paintersHelper, t);
    }));
  }

  public static void createTemporaryBackgroundTransform(JPanel root,
                                                        Image image,
                                                        Fill fill,
                                                        Anchor anchor,
                                                        float alpha,
                                                        Insets insets,
                                                        Disposable disposable) {
    PaintersHelper paintersHelper = new PaintersHelper(root);
    paintersHelper.addPainter(PaintersHelper.newImagePainter(image, fill, anchor, alpha, insets), root);
    Disposer.register(disposable, JBSwingUtilities.addGlobalCGTransform((t, v) -> {
      if (!UIUtil.isAncestor(root, t)) {
        return v;
      }
      return MyGraphics.wrap(v, paintersHelper, t);
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
    return StringUtil.isNotEmpty(getBackgroundSpec(project, EDITOR_PROP));
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

  private static final class MyGraphics extends Graphics2DDelegate {
    final PaintersHelper helper;
    final PaintersHelper.Offsets offsets;
    Predicate<? super Color> preserved;

    static Graphics2D wrap(Graphics g, PaintersHelper helper, JComponent component) {
      MyGraphics gg = g instanceof MyGraphics ? (MyGraphics)g : null;
      return new MyGraphics(gg != null ? gg.myDelegate : g, helper, helper.computeOffsets(g, component), gg != null ? gg.preserved : null);
    }

    static Graphics2D unwrap(Graphics g) {
      return g instanceof MyGraphics ? ((MyGraphics)g).getDelegate() : (Graphics2D)g;
    }

    MyGraphics(Graphics g, PaintersHelper helper, PaintersHelper.Offsets offsets, Predicate<? super Color> preserved) {
      super((Graphics2D)g);
      this.helper = helper;
      this.offsets = offsets;
      this.preserved = preserved;
    }

    @Override
    public @NotNull Graphics create() {
      return new MyGraphics(getDelegate().create(), helper, offsets, preserved);
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

    private static @Nullable Shape calcTempClip(@Nullable Shape prevClip, @NotNull Shape forcedClip) {
      if (prevClip == null) {
        return forcedClip;
      }
      else if (prevClip instanceof Rectangle2D && forcedClip instanceof Rectangle2D) {
        Rectangle2D r = ((Rectangle2D)prevClip).createIntersection((Rectangle2D)forcedClip);
        return r.isEmpty() ? null : r;
      }
      else {
        Area area = new Area(prevClip);
        area.intersect(new Area(forcedClip));
        return area.getBounds().isEmpty() ? null : area;
      }
    }

    void runAllPainters(int x, int y, int width, int height, @Nullable Shape sourceShape, @Nullable Object reason) {
      if (width <= 1 || height <= 1) return;
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

      Shape prevClip = getClip();
      Shape tmpClip = calcTempClip(prevClip, sourceShape != null ? sourceShape : new Rectangle(x, y, width, height));
      if (tmpClip == null) return;

      boolean preserve = preserved != null && reason instanceof Color && preserved.test((Color)reason);
      if (preserve) {
        myDelegate.setRenderingHint(ADJUST_ALPHA, Boolean.TRUE);
      }
      setClip(tmpClip);
      helper.runAllPainters(myDelegate, offsets);
      setClip(prevClip);
      if (preserve) {
        myDelegate.setRenderingHint(ADJUST_ALPHA, Boolean.FALSE);
      }
    }
  }

  private static final class MyTransform implements BiFunction<JComponent, Graphics2D, Graphics2D> {
    @Override
    public Graphics2D apply(JComponent c, Graphics2D g) {
      if (Boolean.TRUE.equals(ClientProperty.get(c, NO_BACKGROUND))) {
        return g;
      }
      String type = getComponentType(c);
      if (type == null) return g;
      if ("frame".equals(type)) return withFrameBackground(g, c);

      Editor editor = "editor".equals(type) ? obtainEditor(c) : null;
      if (editor != null) {
        if (!(g instanceof MyGraphics) && Boolean.TRUE.equals(EditorTextField.SUPPLEMENTARY_KEY.get(editor))) return g;
        if (c instanceof EditorComponentImpl && ((EditorImpl)editor).isDumb()) return MyGraphics.unwrap(g);
      }

      Graphics2D gg = withEditorBackground(g, c);
      if (gg instanceof MyGraphics) {
        ((MyGraphics)gg).preserved = editor != null ? getEditorPreserveColorCondition((EditorEx)editor) : getGeneralPreserveColorCondition(c);
      }
      return gg;
    }

    private static Editor obtainEditor(JComponent c) {
      //noinspection CastConflictsWithInstanceof
      return c instanceof EditorComponentImpl ? ((EditorComponentImpl)c).getEditor() :
             c instanceof EditorGutterComponentEx ? CommonDataKeys.EDITOR.getData((DataProvider)c) :
             null;
    }

    private static @NotNull Predicate<Color> getEditorPreserveColorCondition(EditorEx editor) {
      Color background1 = editor.getBackgroundColor();
      Color background2 = editor.getGutterComponentEx().getBackground();
      return color -> color != background1 && color != background2;
    }

    private static @NotNull Predicate<Color> getGeneralPreserveColorCondition(JComponent c) {
      Component view = c instanceof JViewport ? ((JViewport)c).getView() : c;
      Color selection1 = view instanceof JTree ? UIUtil.getTreeSelectionBackground(true) :
                         view instanceof JList ? UIUtil.getListSelectionBackground(true) :
                         view instanceof JTable ? UIUtil.getTableSelectionBackground(true) :
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
