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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

/**
 * Image editor UI
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
class ImageEditorUI {
    private final JPanel rootPane;
    private final JScrollPane scrollPane;
    private final JLayeredPane contentPane;
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

        // Border
        imageComponent.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        // Zoom by wheel listener
        imageComponent.addMouseWheelListener(wheelAdapter);

        // Create layout
        contentPane = new RendererPane();
        contentPane.add(imageComponent);
        scrollPane = new JScrollPane(contentPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Construct UI
        rootPane = new JPanel(new BorderLayout());
        rootPane.add(scrollPane, BorderLayout.CENTER);
        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
        ActionToolbar actionToolbar = actionManager.createActionToolbar(ImageEditorActions.GROUP_TOOLBAR, actionGroup, true);
        rootPane.add(actionToolbar.getComponent(), BorderLayout.NORTH);

        // Set default size
        zoomModel.setSize(new Dimension(image.getWidth(), image.getHeight()));

        // Set content
        document.setValue(image);
    }

    JComponent getRootPane() {
        return rootPane;
    }

    ImageComponent getImageComponent() {
        return imageComponent;
    }

    void dispose() {
        JViewport viewport = scrollPane.getViewport();
        viewport.setView(null);
        imageComponent.removeMouseWheelListener(wheelAdapter);
        imageComponent.getDocument().removeChangeListener(changeListener);
    }

    ImageZoomModel getZoomModel() {
        return zoomModel;
    }

    void repaint() {
        if (contentPane != null && scrollPane != null) {
            contentPane.revalidate();
            scrollPane.revalidate();
            scrollPane.repaint();
        }
    }

    private final class RendererPane extends JLayeredPane {
        protected void paintComponent(Graphics g) {
            Point imageLocation = imageComponent.getLocation();
            Rectangle bounds = contentPane.getBounds();
            imageLocation.x = (bounds.width - imageComponent.getWidth()) / 2;
            imageLocation.y = (bounds.height - imageComponent.getHeight()) / 2;
            imageComponent.setLocation(imageLocation);
            super.paintComponent(g);
        }

    }

    private final class ImageWheelAdapter implements MouseWheelListener {
        public void mouseWheelMoved(MouseWheelEvent e) {
            Options options = OptionsManager.getInstance().getOptions();
            EditorOptions editorOptions = options.getEditorOptions();
            ZoomOptions zoomOptions = editorOptions.getZoomOptions();
            if (zoomOptions.isWheelZooming() && e.isControlDown()) {
                double zoom = zoomModel.getZoomFactor();
                if (e.getWheelRotation() < 0) {
                    zoom *= 0.5d;
                } else {
                    zoom *= 2.0d;
                }
                zoomModel.setZoomFactor(zoom);
            }
        }
    }

    private class ImageZoomModelImpl implements ImageZoomModel {
        public Dimension getSize() {
            return imageComponent.getSize();
        }

        public final void setSize(Dimension size) {
            imageComponent.setSize(size);
            contentPane.setPreferredSize(size);
            repaint();
        }

        public double getZoomFactor() {
            Dimension size = getSize();
            BufferedImage image = imageComponent.getDocument().getValue();
            return (size.getWidth() / (double)image.getWidth() + size.getHeight() / (double)image.getHeight()) / 2.0d;
        }

        public void setZoomFactor(double zoomFactor) {
            // Change current size
            if (zoomFactor > ZoomOptions.MAX_ZOOM_FACTOR) {
                zoomFactor = ZoomOptions.MAX_ZOOM_FACTOR;
            }
            if (zoomFactor < ZoomOptions.MIN_ZOOM_FACTOR) {
                zoomFactor = ZoomOptions.MIN_ZOOM_FACTOR;
            }
            Dimension size = getSize();
            BufferedImage image = imageComponent.getDocument().getValue();
            size.setSize((double)image.getWidth() * zoomFactor, (double)image.getHeight() * zoomFactor);
            setSize(size);
        }
    }

    private class DocumentChangeListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            repaint();
        }
    }
}
