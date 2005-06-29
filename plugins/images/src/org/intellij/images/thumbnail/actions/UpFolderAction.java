/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.AbstractThumbnailViewAction;

/**
 * Level up to browse images.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class UpFolderAction extends AbstractThumbnailViewAction {
    public void actionPerformed(ThumbnailView thumbnailView, AnActionEvent e) {
        VirtualFile root = thumbnailView.getRoot();
        if (root != null) {
            VirtualFile parent = root.getParent();
            if (parent != null) {
                thumbnailView.setRoot(parent);
            }
        }
    }

    public void update(ThumbnailView thumbnailView, AnActionEvent e) {
        VirtualFile root = thumbnailView.getRoot();
        e.getPresentation().setEnabled(root != null ? root.getParent() != null && !thumbnailView.isRecursive() : false);
    }
}
