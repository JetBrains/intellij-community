/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.AbstractThumbnailViewToggleAction;

/**
 * Toggle recursive flag.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class ToggleRecursiveAction extends AbstractThumbnailViewToggleAction {
    public void setSelected(ThumbnailView thumbnailView, AnActionEvent e, boolean state) {
        thumbnailView.setRecursive(state);
    }

    public boolean isSelected(ThumbnailView thumbnailView, AnActionEvent e) {
        return thumbnailView.isRecursive();
    }
}
