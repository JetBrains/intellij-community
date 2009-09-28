package org.intellij.images.thumbnail.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Thumbnail view actions utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ThumbnailViewActionUtil {
    private ThumbnailViewActionUtil() {
    }

    /**
     * Extract current thumbnail view from event context.
     *
     * @param e Action event
     * @return Current {@link org.intellij.images.thumbnail.ThumbnailView} or <code>null</code>
     */
    public static ThumbnailView getVisibleThumbnailView(AnActionEvent e) {
        ThumbnailView thumbnailView = getThumbnailView(e);
        if (thumbnailView != null && thumbnailView.isVisible()) {
            return thumbnailView;
        }
        return null;
    }

    public static ThumbnailView getThumbnailView(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        return (ThumbnailView) dataContext.getData(ThumbnailView.class.getName());
    }

    /**
     * Enable or disable current action from event.
     *
     * @param e Action event
     * @return Enabled value
     */
    public static boolean setEnabled(AnActionEvent e) {
        ThumbnailView thumbnailView = getVisibleThumbnailView(e);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(thumbnailView != null);
        return presentation.isEnabled();
    }
}
