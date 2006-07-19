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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageEditorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Image viewer manager implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageEditorManagerImpl implements ImageEditorManager, ApplicationComponent {
    @NonNls private static final String NAME = "ImageEditorManager";

    @NotNull
    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    @NotNull
    public ImageEditor createImageEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ImageEditorImpl(project, file);
    }

    public void releaseImageEditor(@NotNull ImageEditor editor) {
        if (!editor.isDisposed()) {
            editor.dispose();
        }
    }
}
