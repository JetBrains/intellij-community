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
package org.intellij.images.editor.impl;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.UIHelper;
import org.intellij.images.ImagesBundle;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.options.*;
import org.intellij.images.ui.ImageComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

/**
 * Image editor UI
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorUI extends JPanel implements DataProvider {
    @NonNls private static final String IMAGE_PANEL = "image";
    @NonNls private static final String ERROR_PANEL = "error";

    private final ImageEditor editor;
    private final DeleteProvider deleteProvider;
    private final UIHelper.CopyPasteSupport copyPasteSupport;

    private final ImageZoomModel zoomModel = new ImageZoomModelImpl();
    private final ImageWheelAdapter wheelAdapter = new ImageWheelAdapter();
    private final ChangeListener changeListener = new DocumentChangeListener();
    private final ImageComponent imageComponent = new ImageComponent();
    private final JPanel contentPanel;
    private final JLabel infoLabel;

    ImageEditorUI(ImageEditor editor, EditorOptions editorOptions) {
        this.editor = editor;
        final UIHelper uiHelper = PeerFactory.getInstance().getUIHelper();
        copyPasteSupport = uiHelper.createPsiBasedCopyPasteSupport(editor.getProject(), this, new UIHelper.PsiElementSelector() {
            public PsiElement[] getSelectedElements() {
                return (PsiElement[]) getData(DataConstants.PSI_ELEMENT_ARRAY);
            }
        });

        deleteProvider = uiHelper.createPsiBasedDeleteProvider();
      
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
        ImageContainerPane view = new ImageContainerPane(imageComponent);
        view.addMouseListener(new EditorMouseAdapter());
        view.addMouseListener(new FocusRequester());

        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Zoom by wheel listener
        scrollPane.addMouseWheelListener(wheelAdapter);

        // Construct UI
        setLayout(new BorderLayout());

        ActionManager actionManager = ActionManager.getInstance();
        ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
        ActionToolbar actionToolbar = actionManager.createActionToolbar(
            ImageEditorActions.ACTION_PLACE, actionGroup, true
        );

        JComponent toolbarPanel = actionToolbar.getComponent();
        toolbarPanel.addMouseListener(new FocusRequester());

        JLabel errorLabel = new JLabel(
            ImagesBundle.message("error.broken.image.file.format"),
            Messages.getErrorIcon(), JLabel.CENTER
        );

        JPanel errorPanel = new JPanel(new BorderLayout());
        errorPanel.add(errorLabel, BorderLayout.CENTER);

        contentPanel = new JPanel(new CardLayout());
        contentPanel.add(scrollPane, IMAGE_PANEL);
        contentPanel.add(errorPanel, ERROR_PANEL);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbarPanel, BorderLayout.WEST);
        infoLabel = new JLabel((String)null, JLabel.RIGHT);
        topPanel.add(infoLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        updateInfo();
    }

    private void updateInfo() {
        ImageDocument document = imageComponent.getDocument();
        BufferedImage image = document.getValue();
        if (image != null) {
            ColorModel colorModel = image.getColorModel();
            String format = document.getFormat();
            if (format == null) {
                format = ImagesBundle.message("unknown.format");
            } else {
                format = format.toUpperCase();
            }
            VirtualFile file = editor.getFile();
            infoLabel.setText(
                    ImagesBundle.message("image.info",
                            image.getWidth(), image.getHeight(), format,
                            colorModel.getPixelSize(), file != null ? StringUtil.formatFileSize(file.getLength()) : ""));
        } else {
            infoLabel.setText(null);
        }
    }

    JComponent getContentComponent() {
        return contentPanel;
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

        private void centerComponents() {
            Rectangle bounds = getBounds();
            Point point = imageComponent.getLocation();
            point.x = (bounds.width - imageComponent.getWidth()) / 2;
            point.y = (bounds.height - imageComponent.getHeight()) / 2;
            imageComponent.setLocation(point);
        }

        public void invalidate() {
            centerComponents();
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
            return image != null ? size.getWidth() / (double)image.getWidth() : 0.0d;
        }

        public void setZoomFactor(double zoomFactor) {
            // Change current size
            Dimension size = imageComponent.getCanvasSize();
            BufferedImage image = imageComponent.getDocument().getValue();
            if (image != null) {
                size.setSize((double)image.getWidth() * zoomFactor, (double)image.getHeight() * zoomFactor);
                imageComponent.setCanvasSize(size);
            }

            revalidate();
            repaint();
        }

        private double getMinimumZoomFactor() {
            BufferedImage image = imageComponent.getDocument().getValue();
            return image != null ? 1.0d / image.getWidth() : 0.0d;
        }

        public void zoomOut() {
            double factor = getZoomFactor();
            if (factor > 1.0d) {
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
            ImageDocument document = imageComponent.getDocument();
            BufferedImage value = document.getValue();

            CardLayout layout = (CardLayout)contentPanel.getLayout();
            layout.show(contentPanel, value != null ? IMAGE_PANEL : ERROR_PANEL);

            updateInfo();

            revalidate();
            repaint();
        }
    }

    private class FocusRequester extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            requestFocus();
        }
    }

    private static final class EditorMouseAdapter extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            if (MouseEvent.BUTTON3 == e.getButton() && e.getClickCount() == 1) {
                // Single right click
                ActionManager actionManager = ActionManager.getInstance();
                ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ImageEditorActions.GROUP_POPUP);
                ActionPopupMenu menu = actionManager.createActionPopupMenu(ImageEditorActions.ACTION_PLACE, actionGroup);
                JPopupMenu popupMenu = menu.getComponent();
                popupMenu.pack();
                popupMenu.show(e.getComponent(), e.getX(), e.getY());

                e.consume();
            }
        }
    }


    @Nullable
    public Object getData(String dataId) {

        if (DataConstantsEx.PROJECT.equals(dataId)) {
            return editor.getProject();
        } else if (DataConstantsEx.VIRTUAL_FILE.equals(dataId)) {
            return editor.getFile();
        } else if (DataConstantsEx.VIRTUAL_FILE_ARRAY.equals(dataId)) {
            return new VirtualFile[] {editor.getFile()};
        } else if (DataConstantsEx.PSI_FILE.equals(dataId)) {
            return getData(DataConstantsEx.PSI_ELEMENT);
        } else if (DataConstants.PSI_ELEMENT.equals(dataId)) {
            VirtualFile file = editor.getFile();
            return file != null ? PsiManager.getInstance(editor.getProject()).findFile(file) : null;
        } else if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
            return new PsiElement[] {(PsiElement) getData(DataConstants.PSI_ELEMENT)};
        } else if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
            return copyPasteSupport.getCopyProvider();
        } else if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
            return copyPasteSupport.getCutProvider();
        } else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
            return deleteProvider;
        }

        return null;
    }
}
