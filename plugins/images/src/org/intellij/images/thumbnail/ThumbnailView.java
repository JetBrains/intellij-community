package org.intellij.images.thumbnail;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Thumbnail thumbnail is a component with thumbnails for a set of {@link com.intellij.openapi.vfs.VirtualFile}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ThumbnailView extends Disposable {
    String TOOLWINDOW_ID = "Thumbnails";

    /**
     * Add virtual files to view
     * @param root Root
     */
    void setRoot(@NotNull VirtualFile root);

    /**
     * Return current root
     */
    VirtualFile getRoot();

    boolean isRecursive();

    void setRecursive(boolean recursive);

    /**
     * Show view
     */
    void show();

    /**
     * Hide view
     */
    void hide();

    Project getProject();

    void setTransparencyChessboardVisible(boolean visible);
    boolean isTransparencyChessboardVisible(); 
}
