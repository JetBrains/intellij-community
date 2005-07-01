package org.intellij.images.editor;

import com.intellij.openapi.fileEditor.FileEditor;

public interface ImageFileEditor extends FileEditor {
    ImageEditor getImageEditor();
}
