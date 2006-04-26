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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.Serializable;

/**
 * Image Editor.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorImpl extends UserDataHolderBase implements ImageFileEditor {
    @NonNls private static final String NAME = "ImageFileEditor";
    private final ImageEditor imageEditor;

    ImageFileEditorImpl(Project project, VirtualFile file) {
        ImageEditorManager imageEditorManager = getImageEditorManager();
        imageEditor = imageEditorManager.createImageEditor(project, file);

        // Append file listener
        VirtualFileManager.getInstance().addVirtualFileListener(imageEditor);

        // Set background and grid default options
        Options options = OptionsManager.getInstance().getOptions();
        EditorOptions editorOptions = options.getEditorOptions();
        GridOptions gridOptions = editorOptions.getGridOptions();
        TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
        imageEditor.setGridVisible(gridOptions.isShowDefault());
        imageEditor.setTransparencyChessboardVisible(transparencyChessboardOptions.isShowDefault());
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

    @NotNull
    public String getName() {
        return NAME;
    }

    @NotNull
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
