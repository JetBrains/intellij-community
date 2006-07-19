package org.intellij.images.editor.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditor;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;

/**
 * Editor actions utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ImageEditorActionUtil {
    private ImageEditorActionUtil() {
    }

    /**
     * Extract current editor from event context.
     *
     * @param e Action event
     * @return Current {@link ImageEditor} or <code>null</code>
     */
    public static ImageEditor getValidEditor(AnActionEvent e) {
        ImageEditor editor = getEditor(e);
        if (editor != null && editor.isValid()) {
            return editor;
        }
        return null;
    }

    public static ImageEditor getEditor(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        FileEditor editor = (FileEditor) dataContext.getData(DataConstants.FILE_EDITOR);
        if (editor instanceof ImageFileEditor) {
            ImageFileEditor fileEditor = (ImageFileEditor) editor;
            return fileEditor.getImageEditor();
        }
        return null;
    }

    /**
     * Enable or disable current action from event.
     *
     * @param e Action event
     * @return Enabled value
     */
    public static boolean setEnabled(AnActionEvent e) {
        ImageEditor editor = getValidEditor(e);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(editor != null);
        return presentation.isEnabled();
    }
}
