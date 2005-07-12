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

    @NotNull Project getProject();

    /**
     * Add virtual files to view
     * @param root Root
     */
    void setRoot(@NotNull VirtualFile root);

    /**
     * Return current root
     */
    @NotNull VirtualFile getRoot();

    boolean isRecursive();

    void setRecursive(boolean recursive);

    void setSelected(@NotNull VirtualFile file, boolean selected);
    boolean isSelected(@NotNull VirtualFile file);

    @NotNull VirtualFile[] getSelection();

    /**
     * Scroll to selection. If ToolWindow is not active, then
     * it will perform activatation before scroll.
     */
    void scrollToSelection();

    void setVisible(boolean visible);

    boolean isVisible();

    void activate();

    void setTransparencyChessboardVisible(boolean visible);
    boolean isTransparencyChessboardVisible();
}
