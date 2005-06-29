/** $Id$ */
package org.intellij.images.ui;

import org.intellij.images.editor.ImageDocument;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * UI for {@link ImageComponent}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public class ImageComponentUI extends ComponentUI {
    private static final ImageComponentUI ui = new ImageComponentUI();

    public void paint(Graphics g, JComponent c) {
        ImageComponent ic = (ImageComponent)c;
        if (ic != null) {
            ImageDocument document = ic.getDocument();
            BufferedImage image = document.getValue();
            if (image != null) {
                paintBorder(g, ic);

                Dimension size = ic.getCanvasSize();
                Graphics igc = g.create(2, 2, size.width, size.height);

                // Transparency chessboard
                if (ic.isTransparencyChessboardVisible()) {
                    paintChessboard(igc, ic);
                }

                paintImage(igc, ic);

                // Grid
                if (ic.isGridVisible()) {
                    paintGrid(igc, ic);
                }

                igc.dispose();
            }
        }
    }

    private void paintBorder(Graphics g, ImageComponent ic) {
        Dimension size = ic.getSize();
        g.setColor(ic.getTransparencyChessboardBlackColor());
        g.drawRect(0, 0, size.width - 1, size.height - 1);
    }

    private void paintChessboard(Graphics g, ImageComponent ic) {
        Dimension size = ic.getCanvasSize();
        // Create pattern
        int cellSize = ic.getTransparencyChessboardCellSize();
        int patternSize = 2 * cellSize;
        BufferedImage pattern = new BufferedImage(patternSize, patternSize, BufferedImage.TYPE_INT_ARGB);
        Graphics imageGraphics = pattern.getGraphics();
        imageGraphics.setColor(ic.getTransparencyChessboardWhiteColor());
        imageGraphics.fillRect(0, 0, patternSize, patternSize);
        imageGraphics.setColor(ic.getTransparencyChessboardBlackColor());
        imageGraphics.fillRect(0, cellSize, cellSize, cellSize);
        imageGraphics.fillRect(cellSize, 0, cellSize, cellSize);

        ((Graphics2D)g).setPaint(new TexturePaint(pattern, new Rectangle(0, 0, patternSize, patternSize)));
        g.fillRect(0, 0, size.width, size.height);
    }

    private void paintImage(Graphics g, ImageComponent ic) {
        ImageDocument document = ic.getDocument();
        Dimension size = ic.getCanvasSize();
        g.drawImage(document.getRenderer(), 0, 0, size.width, size.height, ic);
    }

    private void paintGrid(Graphics g, ImageComponent ic) {
        Dimension size = ic.getCanvasSize();
        BufferedImage image = ic.getDocument().getValue();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        double zoomX = (double)size.width / (double)imageWidth;
        double zoomY = (double)size.height / (double)imageHeight;
        double zoomFactor = (zoomX + zoomY) / 2.0d;
        if (zoomFactor >= ic.getGridLineZoomFactor()) {
            g.setColor(ic.getGridLineColor());
            int ls = ic.getGridLineSpan();
            for (int dx = ls; dx < imageWidth; dx += ls) {
                g.drawLine((int)((double)dx * zoomX), 0, (int)((double)dx * zoomX), size.height);
            }
            for (int dy = ls; dy < imageHeight; dy += ls) {
                g.drawLine(0, (int)((double)dy * zoomY), size.width, (int)((double)dy * zoomY));
            }
        }
    }

    public static ComponentUI createUI(JComponent c) {
        return ui;
    }
}
