package org.intellij.images.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * File type manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public abstract class ImageFileTypeManager {
    public static ImageFileTypeManager getInstance() {
        Application application = ApplicationManager.getApplication();
        return application.getComponent(ImageFileTypeManager.class);
    }

    /**
     * Check that file is image.
     *
     * @param file File to check
     */
    public abstract boolean isImage(VirtualFile file);

    public abstract FileType getImageFileType();
}
