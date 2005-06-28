package org.intellij.images.editor;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageFileEditor extends FileEditor {
    ImageEditor getImageEditor();
}
