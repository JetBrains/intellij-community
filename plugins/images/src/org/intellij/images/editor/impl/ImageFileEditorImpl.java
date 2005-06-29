package org.intellij.images.editor.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageEditorManager;
import org.intellij.images.editor.ImageFileEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.options.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Serializable;

/**
 * Image Editor.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorImpl extends UserDataHolderBase implements ImageFileEditor {
    private static final String NAME = "ImageFileEditor";
    private final ImageEditor imageEditor;

    ImageFileEditorImpl(Project project, VirtualFile file) throws IOException {
        ImageEditorManager imageEditorManager = getImageEditorManager();
        imageEditor = imageEditorManager.createImageEditor(project, file);

        BufferedImage image = imageEditor.getDocument().getValue();

        // Append file listener
        VirtualFileManager.getInstance().addVirtualFileListener(imageEditor);

        // Set background and grid default options
        Options options = OptionsManager.getInstance().getOptions();
        EditorOptions editorOptions = options.getEditorOptions();
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        GridOptions gridOptions = editorOptions.getGridOptions();
        TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
        imageEditor.setGridVisible(gridOptions.isShowDefault());
        imageEditor.setTransparencyChessboardVisible(transparencyChessboardOptions.isShowDefault());

        // Set smart zooming behaviour on open
        ZoomOptions zoomOptions = editorOptions.getZoomOptions();
        if (zoomOptions.isSmartZooming()) {
            Dimension prefferedSize = zoomOptions.getPrefferedSize();
            if (prefferedSize.width > image.getWidth() && prefferedSize.height > image.getHeight()) {
                // Resize to preffered size
                // Calculate zoom factor

                double factor = (prefferedSize.getWidth() / (double)image.getWidth() + prefferedSize.getHeight() / (double)image.getHeight()) / 2.0d;
                zoomModel.setZoomFactor(Math.ceil(factor));
            }
        }
    }

    private static ImageEditorManager getImageEditorManager() {
        Application application = ApplicationManager.getApplication();
        return application.getComponent(ImageEditorManager.class);
    }

    public JComponent getComponent() {
        return imageEditor.getComponent();
    }

    public JComponent getPreferredFocusedComponent() {
        return imageEditor.getContentComponent();
    }

    public String getName() {
        return NAME;
    }

    public FileEditorState getState(FileEditorStateLevel level) {
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        return new ImageFileEditorState(
            imageEditor.isTransparencyChessboardVisible(),
            imageEditor.isGridVisible(),
            zoomModel.getZoomFactor());
    }

    public void setState(FileEditorState state) {
        if (state instanceof ImageFileEditorState) {
            ImageFileEditorState editorState = (ImageFileEditorState)state;
            ImageZoomModel zoomModel = imageEditor.getZoomModel();
            imageEditor.setTransparencyChessboardVisible(editorState.backgroundVisible);
            imageEditor.setGridVisible(editorState.gridVisible);
            zoomModel.setZoomFactor(editorState.zoomFactor);
        }
    }

    public boolean isModified() {
        return false;
    }

    public boolean isValid() {
        return true;
    }

    public void selectNotify() {
    }

    public void deselectNotify() {
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
    }

    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    public StructureViewBuilder getStructureViewBuilder() {
        return null;
    }

    void dispose() {
        VirtualFileManager.getInstance().removeVirtualFileListener(imageEditor);
        ImageEditorManager imageEditorManager = getImageEditorManager();
        imageEditorManager.releaseImageEditor(imageEditor);
    }

    public ImageEditor getImageEditor() {
        return imageEditor;
    }

    private static class ImageFileEditorState implements FileEditorState, Serializable {
        private static final long serialVersionUID = -4470317464706072486L;

        private boolean backgroundVisible;
        private boolean gridVisible;
        private double zoomFactor;

        private ImageFileEditorState(boolean backgroundVisible, boolean gridVisible, double zoomFactor) {
            this.backgroundVisible = backgroundVisible;
            this.gridVisible = gridVisible;
            this.zoomFactor = zoomFactor;
        }

        public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
            return otherState instanceof ImageFileEditorState;
        }
    }
}
