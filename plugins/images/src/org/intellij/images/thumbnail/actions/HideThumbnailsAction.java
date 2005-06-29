/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.AbstractThumbnailViewAction;

/**
 * Hide tool window.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final public class HideThumbnailsAction extends AbstractThumbnailViewAction {
    public void actionPerformed(ThumbnailView thumbnailView, AnActionEvent e) {
        thumbnailView.hide();
    }
}
