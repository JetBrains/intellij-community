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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Image editor provider.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorProvider implements ApplicationComponent, FileEditorProvider {
    @NonNls private static final String NAME = "ImageEditorProvider";
    @NonNls private static final String EDITOR_TYPE_ID = "images";

    private final ImageFileTypeManager typeManager;

    ImageFileEditorProvider(ImageFileTypeManager typeManager) {
        this.typeManager = typeManager;
    }

    @NotNull
    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return typeManager.isImage(file);
    }

    @NotNull
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ImageFileEditorImpl(project, file);
    }

    public void disposeEditor(@NotNull FileEditor editor) {
        ImageFileEditorImpl fileEditor = (ImageFileEditorImpl)editor;
        fileEditor.dispose();
    }

    @NotNull
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return new FileEditorState() {
            public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
                return false;
            }
        };
    }

    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    }

    @NotNull
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @NotNull
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
