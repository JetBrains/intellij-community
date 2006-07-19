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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.options.*;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.vfs.IfsUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Image viewer implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorImpl extends VirtualFileAdapter implements ImageEditor {
    private final PropertyChangeListener optionsChangeListener = new OptionsChangeListener();
    private final Project project;
    private final VirtualFile file;
    private final ImageEditorUI editorUI;
    private boolean disposed;

    ImageEditorImpl(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;

        // Options
        Options options = OptionsManager.getInstance().getOptions();
        editorUI = new ImageEditorUI(this, options.getEditorOptions());
        options.addPropertyChangeListener(optionsChangeListener);

        setValue(file);
    }

    private void setValue(VirtualFile file) {
        ImageDocument document = editorUI.getImageComponent().getDocument();
        try {
            BufferedImage previousImage = document.getValue();
            BufferedImage image = IfsUtil.getImage(file);
            document.setValue(image);
            document.setFormat(IfsUtil.getFormat(file));
            if (image != null && previousImage == null) {
                // Set smart zooming behaviour on open
                Options options = OptionsManager.getInstance().getOptions();
                ZoomOptions zoomOptions = options.getEditorOptions().getZoomOptions();
                // Open as actual size
                ImageZoomModel zoomModel = getZoomModel();
                zoomModel.setZoomFactor(1.0d);

                if (zoomOptions.isSmartZooming()) {
                    Dimension prefferedSize = zoomOptions.getPrefferedSize();
                    if (prefferedSize.width > image.getWidth() && prefferedSize.height > image.getHeight()) {
                        // Resize to preffered size
                        // Calculate zoom factor

                        double factor = (prefferedSize.getWidth() / (double) image.getWidth() + prefferedSize.getHeight() / (double) image.getHeight()) / 2.0d;
                        zoomModel.setZoomFactor(Math.ceil(factor));
                    }
                }
            }
        } catch (Exception e) {
            // Error loading image file
            document.setValue(null);
        }
    }

    public boolean isValid() {
        ImageDocument document = editorUI.getImageComponent().getDocument();
        return document.getValue() != null;
    }

    public JComponent getComponent() {
        return editorUI;
    }

    public JComponent getContentComponent() {
        return editorUI.getImageComponent();
    }

    public VirtualFile getFile() {
        return file;
    }

    public Project getProject() {
        return project;
    }

    public ImageDocument getDocument() {
        return editorUI.getImageComponent().getDocument();
    }

    public void setTransparencyChessboardVisible(boolean visible) {
        editorUI.getImageComponent().setTransparencyChessboardVisible(visible);
        editorUI.repaint();
    }

    public boolean isTransparencyChessboardVisible() {
        return editorUI.getImageComponent().isTransparencyChessboardVisible();
    }

    public void setGridVisible(boolean visible) {
        editorUI.getImageComponent().setGridVisible(visible);
        editorUI.repaint();
    }

    public boolean isGridVisible() {
        return editorUI.getImageComponent().isGridVisible();
    }

    public boolean isDisposed() {
        return disposed;
    }

    public ImageZoomModel getZoomModel() {
        return editorUI.getZoomModel();
    }

    public void dispose() {
        Options options = OptionsManager.getInstance().getOptions();
        options.removePropertyChangeListener(optionsChangeListener);
        editorUI.dispose();
        disposed = true;
    }

    public void contentsChanged(VirtualFileEvent event) {
        super.contentsChanged(event);
        if (file.equals(event.getFile())) {
            // Change document
            file.refresh(true, false, new Runnable() {
                public void run() {
                    setValue(file);
                }
            });
        }
    }

    private class OptionsChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            Options options = (Options) evt.getSource();
            EditorOptions editorOptions = options.getEditorOptions();
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
            GridOptions gridOptions = editorOptions.getGridOptions();

            ImageComponent imageComponent = editorUI.getImageComponent();
            imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
            imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
            imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
            imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
            imageComponent.setGridLineSpan(gridOptions.getLineSpan());
            imageComponent.setGridLineColor(gridOptions.getLineColor());
        }
    }
}
