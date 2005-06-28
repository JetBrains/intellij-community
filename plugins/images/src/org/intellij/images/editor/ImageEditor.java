
 package org.intellij.images.editor;

 import com.intellij.openapi.Disposable;
 import com.intellij.openapi.project.Project;
 import com.intellij.openapi.vfs.VirtualFileListener;

 import javax.swing.*;

/**
 * Image viewer.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageEditor extends Disposable, VirtualFileListener {
    /**
     * Get editor project.
     */
    Project getProject();

    /**
     * Return buffered image source that editing.
     */
    ImageDocument getDocument();

    /**
     * Return entire editor component.
     */
    JComponent getComponent();

    /**
     * Return the target of image editing area within entire component,
     * returned by {@link #getComponent()}.
     */
    JComponent getContentComponent();

    /**
     * Return <code>true</code> if editor is already disposed.
     */
    boolean isDisposed();

    /**
     * Return zoom model.
     */
    ImageZoomModel getZoomModel();

    /**
     * Toggle transparency chessboard.
     */
    void setTransparencyChessboardVisible(boolean visible);

    /**
     * Return <code>true</code> if transparency chessboard is visible.
     */
    boolean isTransparencyChessboardVisible();

    /**
     * Toggle grid.
     */
    void setGridVisible(boolean visible);

    /**
     * Return <code>true</code> if grid is visible.
     */
    boolean isGridVisible();
}
