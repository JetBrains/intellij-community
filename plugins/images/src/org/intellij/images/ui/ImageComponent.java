/** $Id$ */
/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.ui;

import org.intellij.images.editor.ImageDocument;
import org.intellij.images.options.GridOptions;
import org.intellij.images.options.TransparencyChessboardOptions;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Image component is draw image box with effects.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class ImageComponent extends JComponent {
    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "ImageComponentUI";

    static {
        UIManager.getDefaults().put(uiClassID, ImageComponentUI.class.getName());
    }

    private final ImageDocument document = new ImageDocumentImpl();
    private final Grid grid = new Grid();
    private final Chessboard chessboard = new Chessboard();

    public ImageComponent() {
        updateUI();
    }

    public ImageDocument getDocument() {
        return document;
    }

    public void setTransparencyChessboardCellSize(int cellSize) {
        int oldValue = chessboard.getCellSize();
        if (oldValue != cellSize) {
            chessboard.setCellSize(cellSize);
            firePropertyChange("TransparencyChessboard.cellSize", oldValue, cellSize);
        }
    }

    public void setTransparencyChessboardWhiteColor(Color color) {
        Color oldValue = chessboard.getWhiteColor();
        if (oldValue != null && !oldValue.equals(color) || oldValue == null && color != null) {
            chessboard.setWhiteColor(color);
            firePropertyChange("TransparencyChessboard.whiteColor", oldValue, color);
        }
    }

    public void setTransparencyChessboardBlankColor(Color color) {
        Color oldValue = chessboard.getBlackColor();
        if (oldValue != null && !oldValue.equals(color) || oldValue == null && color != null) {
            chessboard.setBlackColor(color);
            firePropertyChange("TransparencyChessboard.blackColor", oldValue, color);
        }
    }

    public void setTransparencyChessboardVisible(boolean visible) {
        boolean oldValue = chessboard.isVisible();
        if (oldValue != visible) {
            chessboard.setVisible(visible);
            firePropertyChange("TransparencyChessboard.visible", oldValue, visible);
        }
    }

    public int getTransparencyChessboardCellSize() {
        return chessboard.getCellSize();
    }

    public Color getTransparencyChessboardWhiteColor() {
        return chessboard.getWhiteColor();
    }

    public Color getTransparencyChessboardBlackColor() {
        return chessboard.getBlackColor();
    }

    public boolean isTransparencyChessboardVisible() {
        return chessboard.isVisible();
    }

    public void setGridLineZoomFactor(int lineZoomFactor) {
        int oldValue = grid.getLineZoomFactor();
        if (oldValue != lineZoomFactor) {
            grid.setLineZoomFactor(lineZoomFactor);
            firePropertyChange("Grid.lineZoomFactor", oldValue, lineZoomFactor);
        }
    }

    public void setGridLineSpan(int lineSpan) {
        int oldValue = grid.getLineSpan();
        if (oldValue != lineSpan) {
            grid.setLineSpan(lineSpan);
            firePropertyChange("Grid.lineSpan", oldValue, lineSpan);
        }
    }

    public void setGridLineColor(Color color) {
        Color oldValue = grid.getLineColor();
        if (oldValue != null && !oldValue.equals(color) || oldValue == null && color != null) {
            grid.setLineColor(color);
            firePropertyChange("Grid.lineColor", oldValue, color);
        }
    }

    public void setGridVisible(boolean visible) {
        boolean oldValue = grid.isVisible();
        if (oldValue != visible) {
            grid.setVisible(visible);
            firePropertyChange("Grid.visible", oldValue, visible);
        }
    }

    public int getGridLineZoomFactor() {
        return grid.getLineZoomFactor();
    }

    public int getGridLineSpan() {
        return grid.getLineSpan();
    }

    public Color getGridLineColor() {
        return grid.getLineColor();
    }

    public boolean isGridVisible() {
        return grid.isVisible();
    }

    public void setCanvasSize(int width, int height) {
        setSize(width + 4, height + 4);
    }

    public void setCanvasSize(Dimension dimension) {
        setCanvasSize(dimension.width, dimension.height);
    }

    public Dimension getCanvasSize() {
        Dimension size = getSize();
        return new Dimension(size.width - 4, size.height - 4);
    }

    public String getUIClassID() {
        return uiClassID;
    }

    public void updateUI() {
        setUI(UIManager.getUI(this));
    }

    private static final class ImageDocumentImpl implements ImageDocument {
        private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(0);
        private BufferedImage image;
        private Image renderer;

        public Image getRenderer() {
            return renderer;
        }

        public BufferedImage getValue() {
            return image;
        }

        public void setValue(BufferedImage image) {
            this.image = image;
            this.renderer = image != null ? Toolkit.getDefaultToolkit().createImage(image.getSource()) : null;
            fireChangeEvent(new ChangeEvent(this));
        }

        private void fireChangeEvent(ChangeEvent e) {
            for (ChangeListener listener : listeners) {
                listener.stateChanged(e);
            }
        }

        public void addChangeListener(ChangeListener listener) {
            listeners.add(listener);
        }

        public void removeChangeListener(ChangeListener listener) {
            listeners.remove(listener);
        }
    }

    private static final class Chessboard {
        private int cellSize = TransparencyChessboardOptions.DEFAULT_CELL_SIZE;
        private Color whiteColor = TransparencyChessboardOptions.DEFAULT_WHITE_COLOR;
        private Color blackColor = TransparencyChessboardOptions.DEFAULT_BLACK_COLOR;
        private boolean visible = false;

        public int getCellSize() {
            return cellSize;
        }

        public void setCellSize(int cellSize) {
            this.cellSize = cellSize;
        }

        public Color getWhiteColor() {
            return whiteColor;
        }

        public void setWhiteColor(Color whiteColor) {
            this.whiteColor = whiteColor;
        }

        public Color getBlackColor() {
            return blackColor;
        }

        public void setBlackColor(Color blackColor) {
            this.blackColor = blackColor;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }
    }

    private static final class Grid {
        private int lineZoomFactor = GridOptions.DEFAULT_LINE_ZOOM_FACTOR;
        private int lineSpan = GridOptions.DEFAULT_LINE_SPAN;
        private Color lineColor = GridOptions.DEFAULT_LINE_COLOR;
        private boolean visible = false;

        public int getLineZoomFactor() {
            return lineZoomFactor;
        }

        public void setLineZoomFactor(int lineZoomFactor) {
            this.lineZoomFactor = lineZoomFactor;
        }

        public int getLineSpan() {
            return lineSpan;
        }

        public void setLineSpan(int lineSpan) {
            this.lineSpan = lineSpan;
        }

        public Color getLineColor() {
            return lineColor;
        }

        public void setLineColor(Color lineColor) {
            this.lineColor = lineColor;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }
    }
}
