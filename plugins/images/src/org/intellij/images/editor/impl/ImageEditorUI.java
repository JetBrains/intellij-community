package org.intellij.images.editor.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.options.*;
import org.intellij.images.ui.ImageComponent;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

/**
 * Image editor UI
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorUI extends JPanel {
    private final ImageZoomModel zoomModel = new ImageZoomModelImpl();
    private final ImageWheelAdapter wheelAdapter = new ImageWheelAdapter();
    private final ChangeListener changeListener = new DocumentChangeListener();
    private final ImageComponent imageComponent = new ImageComponent();

    ImageEditorUI(BufferedImage image, EditorOptions editorOptions) {
        ImageDocument document = imageComponent.getDocument();
        document.addChangeListener(changeListener);

        // Set options
        TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
        GridOptions gridOptions = editorOptions.getGridOptions();
        imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
        imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
        imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
        imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
        imageComponent.setGridLineSpan(gridOptions.getLineSpan());
        imageComponent.setGridLineColor(gridOptions.getLineColor());

        // Create layout
        JScrollPane scrollPane = new JScrollPane(new ImageContainerPane(imageComponent));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Zoom by wheel listener
        scrollPane.addMouseWheelListener(wheelAdapter);

        // Construct UI
        setLayout(new BorderLayout());

        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
        ActionToolbar actionToolbar = actionManager.createActionToolbar(
            ImageEditorActions.GROUP_TOOLBAR, actionGroup, true
        );

        FocusRequester focusRequester = new FocusRequester();
        JComponent component = actionToolbar.getComponent();
        component.addMouseListener(focusRequester);
        scrollPane.addMouseListener(focusRequester);

        add(component, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Set content
        document.setValue(image);

    }

    ImageComponent getImageComponent() {
        return imageComponent;
    }

    void dispose() {
        imageComponent.removeMouseWheelListener(wheelAdapter);
        imageComponent.getDocument().removeChangeListener(changeListener);

        removeAll();
    }

    ImageZoomModel getZoomModel() {
        return zoomModel;
    }

    private static final class ImageContainerPane extends JLayeredPane {
        private final ImageComponent imageComponent;

        public ImageContainerPane(ImageComponent imageComponent) {
            this.imageComponent = imageComponent;
            add(imageComponent);
        }

        private void centerImage() {
            Point imageLocation = imageComponent.getLocation();
            Rectangle bounds = getBounds();
            imageLocation.x = (bounds.width - imageComponent.getWidth()) / 2;
            imageLocation.y = (bounds.height - imageComponent.getHeight()) / 2;
            imageComponent.setLocation(imageLocation);
        }

        public void invalidate() {
            centerImage();

            super.invalidate();
        }

        public Dimension getPreferredSize() {
            return imageComponent.getSize();
        }
    }

    private final class ImageWheelAdapter implements MouseWheelListener {
        public void mouseWheelMoved(MouseWheelEvent e) {
            Options options = OptionsManager.getInstance().getOptions();
            EditorOptions editorOptions = options.getEditorOptions();
            ZoomOptions zoomOptions = editorOptions.getZoomOptions();
            if (zoomOptions.isWheelZooming() && e.isControlDown()) {
                if (e.getWheelRotation() < 0) {
                    zoomModel.zoomOut();
                } else {
                    zoomModel.zoomIn();
                }
                e.consume();
            }
        }
    }

    private class ImageZoomModelImpl implements ImageZoomModel {
        public double getZoomFactor() {
            Dimension size = imageComponent.getCanvasSize();
            BufferedImage image = imageComponent.getDocument().getValue();
            return size.getWidth() / (double)image.getWidth();
        }

        public void setZoomFactor(double zoomFactor) {
            // Change current size
            Dimension size = imageComponent.getCanvasSize();
            BufferedImage image = imageComponent.getDocument().getValue();
            size.setSize((double)image.getWidth() * zoomFactor, (double)image.getHeight() * zoomFactor);
            imageComponent.setCanvasSize(size);

            revalidate();
            repaint();
        }

        private double getMinimumZoomFactor() {
            BufferedImage image = imageComponent.getDocument().getValue();
            return 1.0d / image.getWidth();
        }

        public void zoomOut() {
            double factor = getZoomFactor();
            if (factor > 2.0d) {
                // Macro
                setZoomFactor(factor / 2.0d);
            } else {
                // Micro
                double minFactor = getMinimumZoomFactor();
                double stepSize = (1.0d - minFactor) / MICRO_ZOOM_LIMIT;
                int step = (int)Math.ceil((1.0d - factor) / stepSize);

                setZoomFactor(1.0d - stepSize * (step + 1));
            }
        }

        public void zoomIn() {
            double factor = getZoomFactor();
            if (factor >= 1.0d) {
                // Macro
                setZoomFactor(factor * 2.0d);
            } else {
                // Micro
                double minFactor = getMinimumZoomFactor();
                double stepSize = (1.0d - minFactor) / MICRO_ZOOM_LIMIT;
                int step = (int)Math.ceil((1.0d - factor) / stepSize);

                setZoomFactor(1.0d - stepSize * (step - 1));
            }
        }

        public boolean canZoomOut() {
            double factor = getZoomFactor();
            double minFactor = getMinimumZoomFactor();
            double stepSize = (1.0 - minFactor) / MICRO_ZOOM_LIMIT;
            double step = Math.ceil((1.0 - factor) / stepSize);

            return step < MICRO_ZOOM_LIMIT;
        }

        public boolean canZoomIn() {
            double zoomFactor = getZoomFactor();
            return zoomFactor < MACRO_ZOOM_LIMIT;
        }
    }

    private class DocumentChangeListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            revalidate();
            repaint();
        }
    }

    private class FocusRequester extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            requestFocus();
        }
    }
}
