// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.rulerguide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.internal.rulerguide.BasePreferences.*;

final class RulerGuidePainter extends AbstractPainter implements Disposable {

    private static final Logger LOG = Logger.getInstance(RulerGuidePainter.class);

    private final ComponentBoundsFinder finder = new ComponentBoundsFinder();
    private final AtomicReference<JRootPane> rootPane = new AtomicReference<>();
    private Disposable disposable;

    RulerGuidePainter(Disposable parent) {
        Disposer.register(parent, this);
    }

    private void installPainter(@NotNull JRootPane rootPane) {
        Component glassPane = rootPane.getGlassPane();
        if (glassPane instanceof IdeGlassPane) {
            assert disposable == null : "Disposable is not null";
            disposable = Disposer.newDisposable("Ruler Guide");
            Disposer.register(this, disposable);
            ((IdeGlassPane) glassPane).addPainter(glassPane, this, disposable);
        } else {
            ThiefGlassPane glass = new ThiefGlassPane(glassPane);
            rootPane.setGlassPane(glass);
            glass.setVisible(true);
            glass.revalidate();
        }
    }

    public void removePainter() {
        finder.dispose();
        Optional.ofNullable(rootPane.getAndSet(null)).ifPresent(this::removePainter);
    }

    private void removePainter(@NotNull JRootPane rootPane) {
        Component glassPane = rootPane.getGlassPane();
        if (glassPane instanceof IdeGlassPane) {
            Disposer.dispose(disposable);
            disposable = null;
            glassPane.repaint();
        } else if (glassPane instanceof ThiefGlassPane) {
            Component realGlassPane = ((ThiefGlassPane) glassPane).getRealGlassPane();
            rootPane.setGlassPane(realGlassPane);
            realGlassPane.revalidate();
        } else {
            Disposer.dispose(disposable);
            disposable = null;
            LOG.warn("GlassPane may be only IdeGlassPane or ThiefGlassPane ancestor but found " + glassPane);
        }
    }

    public void repaint(Component eventSource, Point eventPoint) {
        JRootPane newRootPane = SwingUtilities.getRootPane(eventSource);
        JRootPane oldRootPane = this.rootPane.getAndSet(newRootPane);

        if (newRootPane != oldRootPane) {
            Optional.ofNullable(oldRootPane).ifPresent(this::removePainter);
            Optional.ofNullable(newRootPane).ifPresent(this::installPainter);
        }

        if (newRootPane != null) {
            Point point = SwingUtilities.convertPoint(eventSource, eventPoint, newRootPane);
            finder.update(newRootPane, point);
            Component glassPane = newRootPane.getGlassPane();
            if (glassPane instanceof IdeGlassPane) {
                setNeedsRepaint(true, glassPane);
            } else if (glassPane instanceof ThiefGlassPane) {
                glassPane.repaint();
            } else {
                throw new IllegalStateException("GlassPane maybe only IdeGlassPane or ThiefGlassPane but found " + glassPane);
            }
        }
    }

    @Override
    public boolean needsRepaint() {
        return finder.getLastResult() != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
        int y = 0;
        if (SystemInfoRt.isMac) {
          var window = ComponentUtil.getWindow(component);
          if (window != null && window.getType() == Window.Type.NORMAL) {
            var pane = ComponentUtil.getParentOfType(JRootPane.class, component);
            ProjectFrameHelper helper = ProjectFrameHelper.getFrameHelper(window);
            if ((helper == null || !helper.isInFullScreen()) && pane != null) {
              y -= UIUtil.getTransparentTitleBarHeight(pane);
            }
          }
        }

        Graphics2D g2d = (Graphics2D) g.create(0, y, component.getWidth(), component.getHeight() - y);
        GraphicsUtil.setupAntialiasing(g2d, false, false);
        ComponentBoundsFinder.Result result = finder.getLastResult();

        ComponentBounds pivot = null;
        for (ComponentBounds bounds : result.getBounds()) {
            if (bounds.contains(result.getPoint())) {
                pivot = bounds;
                g2d.setColor(BACKGROUND_COLOR);
                g2d.fillRect(pivot.x, pivot.y, pivot.width, pivot.height);
                paintBaselines(pivot, g2d);
                break;
            }
        }

        for (ComponentBounds bounds : result.getBounds()) {
            g2d.setColor(BACKGROUND_COLOR);
            if (pivot != null && pivot != bounds) {

                Point baselineOtherPoint = new Point(bounds.x + bounds.verticalBaseline, bounds.y + bounds.horizontalBaseline);
                Point baselinePivotPoint = new Point(pivot.x + pivot.verticalBaseline, pivot.y + pivot.horizontalBaseline);
                int verticalOffset = Math.abs(baselineOtherPoint.x - baselinePivotPoint.x);
                int horizontalOffset = Math.abs(baselineOtherPoint.y - baselinePivotPoint.y);
                if (verticalOffset == 0 || horizontalOffset == 0) {
                    g2d.setColor(FINE_COLOR);
                } else if (verticalOffset <= getAllowedGap() || horizontalOffset <= getAllowedGap()) {
                    g2d.setColor(ERROR_COLOR);
                } else continue;

                g2d.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }

        g2d.dispose();
    }

    private static void paintBaselines(ComponentBounds bounds, Graphics2D g2d) {
        Rectangle clipBounds = g2d.getClipBounds();

        Point location = bounds.getLocation();

        g2d.setColor(COMPONENT_COLOR);
        g2d.drawLine(clipBounds.x, location.y + bounds.height, clipBounds.x + clipBounds.width, location.y + bounds.height);
        int hbaseline = bounds.horizontalBaseline;
        if (hbaseline >= 0) {
            g2d.setColor(BASE_COLOR);
            g2d.drawLine(clipBounds.x, location.y + hbaseline, clipBounds.x + clipBounds.width, location.y + hbaseline);
        }

        g2d.setColor(COMPONENT_COLOR);
        g2d.drawLine(location.x, clipBounds.y, location.x, clipBounds.y + clipBounds.height);

        int vbaseline = bounds.verticalBaseline;
        if (vbaseline >= 0) {
            g2d.setColor(BASE_COLOR);
            g2d.drawLine(location.x + vbaseline, clipBounds.y, location.x + vbaseline, clipBounds.y + clipBounds.height);

        }
    }

    @Override
    public void dispose() {
        removePainter();
    }

    // steal own glass pane and using our
    private final class ThiefGlassPane extends JComponent {
        private final Component realGlassPane;

        private ThiefGlassPane(Component realGlassPane) {
            this.realGlassPane = realGlassPane;
            setOpaque(false);
        }

        public Component getRealGlassPane() {
            return realGlassPane;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (g instanceof SneakyGraphics2D || isJBPopupMenu()) {
                return;
            }
            executePaint(SwingUtilities.getRoot(this), (Graphics2D) g);
        }

        private boolean isJBPopupMenu() {
            // fixme JBPopupMenu[97] has own timer, that repaints only own menu
            Container contentPane = SwingUtilities.getRootPane(this).getContentPane();
            return contentPane.getComponentCount() == 1 && contentPane.getComponent(0) instanceof JBPopupMenu;
        }
    }
}
