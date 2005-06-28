package org.intellij.images.editor.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageEditorManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Image viewer manager implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
class ImageEditorManagerImpl implements ImageEditorManager, ApplicationComponent {
    private static final String NAME = "ImageEditorManager";

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    @NotNull
    public ImageEditor createImageEditor(@NotNull Project project, @NotNull VirtualFile file) throws IOException {
        return new ImageEditorImpl(project, file);
    }

    public void releaseImageEditor(@NotNull ImageEditor editor) {
        if (!editor.isDisposed()) {
            editor.dispose();
        }
    }
}
